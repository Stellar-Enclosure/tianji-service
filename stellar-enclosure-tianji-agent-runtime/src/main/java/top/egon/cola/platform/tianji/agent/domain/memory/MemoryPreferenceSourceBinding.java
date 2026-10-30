package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Deployment approval for one immutable definition's user-input source and personal target scope. */
public record MemoryPreferenceSourceBinding(String id, long tenantId, String issuerFingerprint, String definitionKey,
                                             long definitionVersion, String definitionSha256, String scopeId, String policyRef) {
    public MemoryPreferenceSourceBinding {
        for (var value : List.of(id, definitionKey, scopeId)) if (!identifier(value, 128)) throw invalid();
        if (tenantId < 1 || definitionVersion < 1 || issuerFingerprint == null || !issuerFingerprint.matches("[0-9a-f]{64}")
                || definitionSha256 == null || !definitionSha256.matches("[0-9a-f]{64}") || policyRef == null
                || !policyRef.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}@[1-9][0-9]{0,18}#[0-9a-f]{64}")) throw invalid();
    }

    public boolean accepts(TrustedAgentActor actor, ExtractMemoryPreferencesCommand request) {
        return actor.subjectType() == TrustedAgentActor.SubjectType.USER && actor.tenantId() == tenantId && issuerFingerprint.equals(actor.issuerFingerprint())
                && id.equals(request.bindingId()) && scopeId.equals(request.scopeId()) && policyRef.equals(request.policyRef())
                && request.owner().type() == MemoryOwnerTypeEnum.USER && request.owner().id().equals(actor.subjectId());
    }

    public boolean matches(AgentRunAuthorization.Request run) {
        if (run.actor().subjectType() != TrustedAgentActor.SubjectType.USER || run.actor().tenantId() != tenantId || !issuerFingerprint.equals(run.actor().issuerFingerprint())
                || !definitionKey.equals(run.definitionKey()) || definitionVersion != run.definitionVersion()) return false;
        if (!definitionSha256.equals(run.definitionSha256())) throw invalid();
        return true;
    }
    private static boolean identifier(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory preference source binding is invalid"); }
    @Override public String toString() { return "MemoryPreferenceSourceBinding[redacted]"; }
}
