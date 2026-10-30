package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deployment-owned source selection. Its digest binds a separate Memory-read intent; request data cannot select an owner. */
public record MemoryContextBinding(String id, long tenantId, String issuerFingerprint, TrustedAgentActor.SubjectType principalType,
                                   String definitionKey, long definitionVersion, String definitionSha256, MemoryOwnerTypeEnum ownerType,
                                   String teamId, String scopeId, MemoryKindEnum kind, boolean semantic, int limit, Set<String> retentionPolicyRefs) {
    public MemoryContextBinding {
        principalType = Objects.requireNonNull(principalType, "principalType"); ownerType = Objects.requireNonNull(ownerType, "ownerType");
        for (var value : List.of(id, definitionKey, scopeId)) if (!identifier(value, 128)) throw invalid();
        if (tenantId < 1 || definitionVersion < 1 || !hash(issuerFingerprint) || !hash(definitionSha256) || limit < 1 || limit > 32
                || ownerType == MemoryOwnerTypeEnum.APPLICATION && principalType != TrustedAgentActor.SubjectType.SERVICE
                || ownerType != MemoryOwnerTypeEnum.APPLICATION && principalType != TrustedAgentActor.SubjectType.USER
                || ownerType == MemoryOwnerTypeEnum.TEAM && !identifier(teamId, 256) || ownerType != MemoryOwnerTypeEnum.TEAM && teamId != null) throw invalid();
        retentionPolicyRefs = Set.copyOf(Objects.requireNonNull(retentionPolicyRefs, "retentionPolicyRefs"));
        if (retentionPolicyRefs.isEmpty() || retentionPolicyRefs.size() > 32) throw invalid();
        for (var reference : retentionPolicyRefs) {
            if (!reference.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}@[1-9][0-9]{0,18}#[0-9a-f]{64}")) throw invalid();
            try { Long.parseLong(reference.substring(reference.lastIndexOf('@') + 1, reference.lastIndexOf('#'))); }
            catch (NumberFormatException invalid) { throw invalid(); }
        }
    }

    /** A hash mismatch for the same immutable definition identity is drift, never an unconfigured empty source. */
    public boolean matches(AgentRunAuthorization.Request run) {
        Objects.requireNonNull(run, "run"); var actor = run.actor();
        if (tenantId != actor.tenantId() || !issuerFingerprint.equals(actor.issuerFingerprint()) || principalType != actor.subjectType()
                || !definitionKey.equals(run.definitionKey()) || definitionVersion != run.definitionVersion()) return false;
        if (!definitionSha256.equals(run.definitionSha256())) throw invalid(); return true;
    }

    public MemoryOwnerDTO owner(TrustedAgentActor actor) {
        if (actor == null || actor.tenantId() != tenantId || !issuerFingerprint.equals(actor.issuerFingerprint()) || principalType != actor.subjectType()) throw invalid();
        return new MemoryOwnerDTO(ownerType, ownerType == MemoryOwnerTypeEnum.TEAM ? teamId : actor.subjectId());
    }

    public MemoryReadGrant.Scope scope(AgentRunAuthorization.Request run, AgentServiceExecutionGrant.Subject serviceSubject) {
        if (!matches(run) || principalType == TrustedAgentActor.SubjectType.USER && serviceSubject != null
                || principalType == TrustedAgentActor.SubjectType.SERVICE && serviceSubject == null) throw invalid();
        return new MemoryReadGrant.Scope(owner(run.actor()), scopeId, fingerprint(), serviceSubject);
    }

    public String fingerprint() {
        var fields = new ArrayList<>(List.of("TIANJI_MEMORY_CONTEXT_BINDING_V1", id, Long.toString(tenantId), issuerFingerprint, principalType.name(), definitionKey,
                Long.toString(definitionVersion), definitionSha256, ownerType.name(), teamId == null ? "" : teamId, scopeId, kind == null ? "" : kind.name(), Boolean.toString(semantic), Integer.toString(limit)));
        fields.addAll(retentionPolicyRefs.stream().sorted().toList());
        var framed = new StringBuilder(); for (var field : fields) framed.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(framed.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("Memory source binding digest is unavailable"); }
    }
    public static List<MemoryContextBinding> validate(List<MemoryContextBinding> configured) {
        var bindings = List.copyOf(configured); if (bindings.isEmpty() || bindings.size() > 1000) throw invalid();
        var ids = new HashSet<String>(); var scopes = new HashSet<List<String>>(); var hashes = new HashMap<List<String>, String>();
        var counts = new HashMap<List<String>, Integer>(); var limits = new HashMap<List<String>, Integer>();
        for (var binding : bindings) {
            var key = List.of(Long.toString(binding.tenantId()), binding.issuerFingerprint(), binding.principalType().name(), binding.definitionKey(), Long.toString(binding.definitionVersion()));
            var scope = new ArrayList<>(key); scope.add(binding.ownerType().name()); scope.add(binding.teamId() == null ? "" : binding.teamId()); scope.add(binding.scopeId());
            String previous = hashes.putIfAbsent(key, binding.definitionSha256());
            if (!ids.add(binding.id()) || !scopes.add(List.copyOf(scope)) || previous != null && !previous.equals(binding.definitionSha256())
                    || counts.merge(key, 1, Integer::sum) > 16 || limits.merge(key, binding.limit(), Integer::sum) > 64) throw invalid();
        }
        return bindings;
    }
    @Override public String toString() { return "MemoryContextBinding[redacted]"; }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean identifier(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory context source binding is invalid"); }
}
