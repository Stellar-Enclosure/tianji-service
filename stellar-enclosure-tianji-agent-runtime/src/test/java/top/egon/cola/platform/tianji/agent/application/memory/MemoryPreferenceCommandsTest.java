package top.egon.cola.platform.tianji.agent.application.memory;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MemoryPreferenceCommandsTest {
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final String RETENTION = "retention@1#" + "a".repeat(64);

    @Test void request_time_and_background_paths_share_the_existing_byte_stable_source_write_identity() {
        var policy = policy(1); var request = request(policy); var prepared = prepared(policy);
        var commands = MemoryPreferenceCommands.create(ACTOR, request, policy, prepared); var command = commands.getFirst();
        assertEquals("d7f76eaa-102f-39c2-a280-2ae0a649bb4b", command.commandId());
        assertEquals("pref-7cd8f8f1117938abd3193231b7d16b938dd58f72e7b4cc8bd67c69723a7caf63", command.memoryId());
        assertFalse(command.confirmed()); assertEquals(0, command.expectedRevision()); assertNull(command.effectiveAt()); assertNull(command.expiresAt());
        assertEquals("run", command.source().sourceRunId()); assertEquals("terminal", command.source().sourceEventId()); assertTrue(command.source().reference().endsWith(":0:32"));
        assertEquals(prepared.sourceSha256(), command.source().evidenceSha256()); assertThrows(UnsupportedOperationException.class, commands::clear);
        var changedPolicy = policy(2); var changed = MemoryPreferenceCommands.create(ACTOR, request(changedPolicy), changedPolicy, prepared(changedPolicy)).getFirst();
        assertEquals(command.commandId(), changed.commandId()); assertEquals(command.memoryId(), changed.memoryId()); assertNotEquals(command, changed);
    }

    @Test void wrong_owner_policy_or_duplicate_proposals_cannot_become_candidate_commands() {
        var policy = policy(1); var prepared = prepared(policy);
        var other = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "other", "https://idp.example/20");
        assertThrows(IllegalArgumentException.class, () -> MemoryPreferenceCommands.create(other, request(policy), policy, prepared));
        assertThrows(IllegalArgumentException.class, () -> MemoryPreferenceCommands.create(ACTOR, request(policy), policy(2), prepared));
        var duplicate = new MemoryPreferenceExtraction.Result(prepared.policyRef(), prepared.sourceSha256(), prepared.outcome(), List.of(prepared.proposals().getFirst(), prepared.proposals().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> MemoryPreferenceCommands.create(ACTOR, request(policy), policy, duplicate));
        var empty = new MemoryPreferenceExtraction().evaluate(new MemoryPreferenceExtraction.Source(AgentRunStatusEnum.COMPLETED, MemoryPreferenceExtraction.SourceKind.USER_INPUT, true, "no declaration"), policy);
        assertTrue(MemoryPreferenceCommands.create(ACTOR, request(policy), policy, empty).isEmpty());
    }

    private static MemoryExtractionPolicy policy(long version) { return new MemoryExtractionPolicy("preferences", version, Map.of("language", Set.of("zh-CN")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3); }
    private static ExtractMemoryPreferencesCommand request(MemoryExtractionPolicy policy) { return new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", "run", "terminal", "source", policy.reference()); }
    private static MemoryPreferenceExtraction.Result prepared(MemoryExtractionPolicy policy) { return new MemoryPreferenceExtraction().evaluate(new MemoryPreferenceExtraction.Source(AgentRunStatusEnum.COMPLETED, MemoryPreferenceExtraction.SourceKind.USER_INPUT, true, "memory.preference.language=zh-CN"), policy); }
}
