package top.egon.cola.platform.tianji.agent.domain.tool;

import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

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
import java.util.Objects;

/** Fixed schema, binding and policy content. Host registration never obtains execution authority from a model's name or arguments. */
public record ToolDescriptor(String key, long version, String name, String description, ToolSchema input, ToolSchema output, ToolPolicy policy) {
    public ToolDescriptor {
        input = Objects.requireNonNull(input, "input"); output = Objects.requireNonNull(output, "output"); policy = Objects.requireNonNull(policy, "policy");
        if (!ToolPolicy.key(key) || version < 1 || name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || description == null
                || description.length() > 2048 || description.indexOf(0) >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(description)) throw new IllegalArgumentException("Tool descriptor is invalid");
    }
    public String versionKey() { return versionKey(key, version); }
    public String parametersSha256(Map<String, Object> values) {
        return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_INVOCATION_PARAMETERS_V1", key, Long.toString(version), reference().contentSha256()), input.values(values));
    }
    public DefinitionResourceRef reference() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var target = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                for (var value : new String[]{"TIANJI_TOOL_DESCRIPTOR_V1", key, Long.toString(version), name, description, policy.risk().name(), policy.actionKey(), policy.executionResourceKey(), policy.bindingSha256(), Objects.requireNonNullElse(policy.approvalPolicyRef(), "")}) ToolSchema.text(target, value);
                input.writeTo(target); output.writeTo(target);
            }
            return new DefinitionResourceRef(key, version, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException | IOException unavailable) { throw new IllegalStateException("Tool descriptor fingerprint is unavailable"); }
    }
    static String versionKey(String key, long version) { if (!ToolPolicy.key(key) || version < 1) throw new IllegalArgumentException("Tool version reference is invalid"); return key + '@' + version; }
}
