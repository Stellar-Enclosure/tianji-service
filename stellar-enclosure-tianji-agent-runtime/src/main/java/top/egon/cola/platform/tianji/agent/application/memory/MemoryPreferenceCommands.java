package top.egon.cola.platform.tianji.agent.application.memory;

import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One canonical command factory for request-time and independently authorized background preference writes. It grants no authority. */
public final class MemoryPreferenceCommands {
    private MemoryPreferenceCommands() { }

    public static List<UpsertMemoryCommand> create(TrustedAgentActor actor, ExtractMemoryPreferencesCommand command,
                                                  MemoryExtractionPolicy policy, MemoryPreferenceExtraction.Result prepared) {
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(command, "command"); Objects.requireNonNull(policy, "policy"); Objects.requireNonNull(prepared, "prepared");
        if (actor.subjectType() != TrustedAgentActor.SubjectType.USER || "LEGACY".equals(actor.issuerFingerprint()) || command.owner().type() != MemoryOwnerTypeEnum.USER
                || !actor.subjectId().equals(command.owner().id()) || !policy.reference().equals(command.policyRef()) || !prepared.policyRef().equals(command.policyRef())
                || prepared.proposals().size() > policy.maximumCandidates()) throw invalid();
        var commands = new ArrayList<UpsertMemoryCommand>(); var keys = new HashSet<String>();
        for (var proposal : prepared.proposals()) {
            var fact = proposal.content().facts().entrySet().iterator().next(); String name = fact.getKey();
            if (!keys.add(name) || !policy.preferenceValues().getOrDefault(name, Set.of()).contains(fact.getValue()) || !policy.candidateConfidence().equals(proposal.confidence())
                    || policy.sensitivity() != proposal.sensitivity() || !policy.retentionPolicyRef().equals(proposal.retentionPolicyRef())) throw invalid();
            String reference = "memory-preference:" + command.bindingId() + '#' + policy.reference().substring(policy.reference().lastIndexOf('#') + 1)
                    + ':' + proposal.sourceStart() + ':' + proposal.sourceEnd();
            var source = new MemorySourceDTO(reference, prepared.sourceSha256(), command.runId(), command.sourceEventId());
            commands.add(new UpsertMemoryCommand(command.itemCommandId(name), command.owner(), command.scopeId(), memoryId(actor, command, name), proposal.kind(), 0,
                    proposal.content(), source, proposal.confidence(), false, proposal.sensitivity(), proposal.retentionPolicyRef(), null, null));
        }
        return List.copyOf(commands);
    }

    private static String memoryId(TrustedAgentActor actor, ExtractMemoryPreferencesCommand command, String name) {
        var data = new StringBuilder("TIANJI_MEMORY_PREFERENCE_ID_V1:");
        for (var field : List.of(actor.identityFingerprint(), command.runId(), command.sourceEventId(), command.scopeId(), name)) data.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        try { return "pref-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory preference command binding is invalid"); }
}
