package top.egon.cola.platform.tianji.agent.domain;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentExecutionGrantTest {
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final Instant DEADLINE = Instant.parse("2026-10-30T00:00:00.123456Z");
    private static final String RUN = "00000000-0000-0000-0000-000000000123";
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER,
            "alice", "https://idp.example/tenants/21");

    @Test void binding_changes_for_every_identity_and_immutable_execution_dimension() {
        var original = request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE);
        String digest = AgentExecutionGrant.fingerprint(original, RESOURCE);
        assertEquals(digest, AgentExecutionGrant.fingerprint(original, RESOURCE));
        assertTrue(digest.matches("[0-9a-f]{64}")); assertFalse(digest.contains("alice"));
        var variants = new ArrayList<AgentRunAuthorization.Request>();
        variants.add(request(TrustedAgentActor.verified(22, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/tenants/21"), RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/tenants/21"), RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://other.example/tenants/21"), RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(ACTOR, "00000000-0000-0000-0000-000000000124", "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(ACTOR, RUN, "different", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(ACTOR, RUN, "definition", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(ACTOR, RUN, "definition", 1, "d".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(ACTOR, RUN, "definition", 1, "a".repeat(64), "d".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "d".repeat(64), "policy-v1", true, DEADLINE));
        variants.add(request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v2", true, DEADLINE));
        variants.add(request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE));
        variants.add(request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE.plusNanos(1000)));
        for (var changed : variants) assertNotEquals(digest, AgentExecutionGrant.fingerprint(changed, RESOURCE));
        assertNotEquals(digest, AgentExecutionGrant.fingerprint(original, URI.create("https://other.example/tianji")));
    }

    @Test void legacy_service_or_invalid_resource_cannot_form_a_user_grant() {
        for (var actor : List.of(new TrustedAgentActor(21, TrustedAgentActor.SubjectType.USER, "alice"),
                TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "agent", "https://idp.example/tenants/21"))) {
            assertThrows(IllegalArgumentException.class, () -> AgentExecutionGrant.fingerprint(request(actor, RUN, "definition", 1,
                    "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE), RESOURCE));
        }
        var original = request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE);
        assertThrows(IllegalArgumentException.class, () -> AgentExecutionGrant.fingerprint(original, URI.create("http://api.example/tianji")));
        assertThrows(IllegalArgumentException.class, () -> AgentExecutionGrant.fingerprint(original, URI.create("https://api.example/a/../tianji")));
        assertThrows(IllegalArgumentException.class, () -> AgentExecutionGrant.fingerprint(request(ACTOR, "not-uuid", "definition", 1,
                "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE), RESOURCE));
    }

    @Test void sensitive_evidence_is_redacted_and_structurally_bounded() {
        var ticket = new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE);
        assertFalse(ticket.toString().contains("sealed"));
        assertThrows(IllegalArgumentException.class, () -> new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "not-sealed", DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:" + "a".repeat(32_769), DEADLINE));
    }

    private static AgentRunAuthorization.Request request(TrustedAgentActor actor, String id, String key, long version, String definition,
            String input, String snapshot, String policy, boolean session, Instant deadline) {
        return new AgentRunAuthorization.Request(actor, id, key, version, definition, input, snapshot, policy, session, deadline);
    }
}
