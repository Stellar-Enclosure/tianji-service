package top.egon.cola.platform.tianji.agent.domain.memory;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;

import java.time.Instant;
import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryContextBindingTest {
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final String HASH = "a".repeat(64);
    private static final String POLICY = "retention@1#" + "b".repeat(64);

    @Test void scope_comes_from_the_verified_actor_and_immutable_source_selection() {
        var personal = binding(MemoryOwnerTypeEnum.USER, null, 5); var run = run(HASH);
        assertTrue(personal.matches(run)); assertEquals("alice", personal.owner(ACTOR).id());
        assertEquals(personal.fingerprint(), personal.scope(run, null).sourceBindingSha256());
        var team = binding(MemoryOwnerTypeEnum.TEAM, "reviewed-team", 5);
        assertEquals("reviewed-team", team.owner(ACTOR).id()); assertNotEquals(personal.fingerprint(), team.fingerprint());
        assertNotEquals(personal.fingerprint(), binding(MemoryOwnerTypeEnum.USER, null, 6).fingerprint());
        assertThrows(IllegalArgumentException.class, () -> personal.owner(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20")));
        assertThrows(IllegalArgumentException.class, () -> personal.owner(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "alice", "https://idp.example/20")));
    }

    @Test void changed_hash_for_the_same_definition_cannot_silently_remove_the_source() {
        var selected = binding(MemoryOwnerTypeEnum.USER, null, 5);
        assertThrows(IllegalArgumentException.class, () -> selected.matches(run("c".repeat(64))));
        assertFalse(selected.matches(new AgentRunAuthorization.Request(ACTOR, "run", "other-definition", 1, HASH, HASH, HASH, "policy", false, Instant.now().plusSeconds(60))));
        assertThrows(IllegalArgumentException.class, () -> binding(MemoryOwnerTypeEnum.USER, "untrusted-owner", 5));
        assertThrows(IllegalArgumentException.class, () -> binding(MemoryOwnerTypeEnum.APPLICATION, null, 5));
        assertThrows(IllegalArgumentException.class, () -> new MemoryContextBinding("source", 20, ACTOR.issuerFingerprint(), ACTOR.subjectType(), "definition", 1, HASH,
                MemoryOwnerTypeEnum.USER, null, "research", null, true, 5, Set.of("retention@1#" + "z".repeat(64))));
    }

    private static MemoryContextBinding binding(MemoryOwnerTypeEnum owner, String team, int limit) {
        return new MemoryContextBinding("source", 20, ACTOR.issuerFingerprint(), ACTOR.subjectType(), "definition", 1, HASH, owner, team, "research", MemoryKindEnum.PREFERENCE, true, limit, Set.of(POLICY));
    }
    @Test void an_authorization_request_cannot_ambiguously_select_two_sources_for_the_same_owner_scope() {
        var first = binding(MemoryOwnerTypeEnum.USER, null, 5);
        var second = new MemoryContextBinding("other-source", 20, ACTOR.issuerFingerprint(), ACTOR.subjectType(), "definition", 1, HASH,
                MemoryOwnerTypeEnum.USER, null, "research", MemoryKindEnum.FACT, false, 5, Set.of(POLICY));
        assertThrows(IllegalArgumentException.class, () -> MemoryContextBinding.validate(List.of(first, second)));
        assertDoesNotThrow(() -> MemoryContextBinding.validate(List.of(first,
                new MemoryContextBinding("team-source", 20, ACTOR.issuerFingerprint(), ACTOR.subjectType(), "definition", 1, HASH,
                        MemoryOwnerTypeEnum.TEAM, "team", "research", MemoryKindEnum.PREFERENCE, true, 5, Set.of(POLICY)))));
    }
    private static AgentRunAuthorization.Request run(String hash) { return new AgentRunAuthorization.Request(ACTOR, "run", "definition", 1, hash, HASH, HASH, "policy", false, Instant.now().plusSeconds(60)); }
}
