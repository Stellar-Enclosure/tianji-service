package top.egon.cola.platform.tianji.agent.domain.tool;

import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Stable business-action request, independent of model tool-call IDs. It is not an authorization, approval or execution receipt. */
public final class ToolActionRequest {
    private final TrustedAgentActor owner;
    private final DefinitionResourceRef tool;
    private final ToolPolicy policy;
    private final String actionKey;
    private final String businessKey;
    private final String businessVersion;
    private final Map<String, Object> arguments;
    private final String intentId;
    private final String requestSha256;

    public ToolActionRequest(TrustedAgentActor owner, ToolDescriptor descriptor, String businessKey, String businessVersion, Map<String, Object> arguments) {
        this.owner = Objects.requireNonNull(owner, "owner"); Objects.requireNonNull(descriptor, "descriptor");
        if ("LEGACY".equals(owner.issuerFingerprint()) || descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY) throw invalid();
        this.businessKey = reference(businessKey, 255); this.businessVersion = reference(businessVersion, 128); this.tool = descriptor.reference(); this.policy = descriptor.policy(); this.actionKey = policy.actionKey();
        this.arguments = descriptor.input().values(arguments);
        String key = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_ACTION_INTENT_V1", owner.identityFingerprint(), actionKey, this.businessKey, this.businessVersion), Map.of());
        var bytes = ByteBuffer.wrap(HexFormat.of().parseHex(key)); intentId = new UUID(bytes.getLong(), bytes.getLong()).toString();
        requestSha256 = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_ACTION_REQUEST_V1", intentId, owner.identityFingerprint(), actionKey, this.businessKey, this.businessVersion,
                tool.key(), Long.toString(tool.version()), tool.contentSha256()), this.arguments);
    }
    public TrustedAgentActor owner() { return owner; }
    public DefinitionResourceRef tool() { return tool; }
    public ToolPolicy policy() { return policy; }
    public String actionKey() { return actionKey; }
    public String businessKey() { return businessKey; }
    public String businessVersion() { return businessVersion; }
    public Map<String, Object> arguments() { return arguments; }
    public String intentId() { return intentId; }
    public String requestSha256() { return requestSha256; }
    @Override public String toString() { return "ToolActionRequest[redacted]"; }
    private static String reference(String value, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maximum || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                || value.chars().anyMatch(Character::isISOControl)) throw invalid(); return value;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool action business binding is invalid"); }
}
