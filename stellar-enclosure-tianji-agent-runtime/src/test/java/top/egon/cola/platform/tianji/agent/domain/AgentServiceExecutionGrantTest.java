package top.egon.cola.platform.tianji.agent.domain;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentServiceExecutionGrantTest {
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final URI SOURCE = URI.create("https://api.example/process");
    private static final Instant DEADLINE = Instant.parse("2026-10-30T00:00:00.123456Z");
    private static final String RUN = "00000000-0000-0000-0000-000000000123";
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE,
            "worker", "https://idp.example/tenants/21");
    private static final AgentServiceExecutionGrant.Subject SUBJECT = subject("worker", "process", "platform", "process", "test", SOURCE);

    @Test void fingerprint_binds_the_service_tuple_and_every_immutable_run_dimension() {
        var original = request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE);
        String digest = AgentServiceExecutionGrant.fingerprint(original, RESOURCE, SUBJECT);
        assertTrue(digest.matches("[0-9a-f]{64}"));
        assertEquals(digest, AgentServiceExecutionGrant.fingerprint(original, RESOURCE, SUBJECT));
        assertFalse(digest.contains("worker"));
        for (var changed : List.of(
                subject("worker", "other", "platform", "process", "test", SOURCE),
                subject("worker", "process", "other", "process", "test", SOURCE),
                subject("worker", "process", "platform", "other", "test", SOURCE),
                subject("worker", "process", "platform", "process", "prod", SOURCE),
                subject("worker", "process", "platform", "process", "test", URI.create("https://api.example/other")))) {
            assertNotEquals(digest, AgentServiceExecutionGrant.fingerprint(original, RESOURCE, changed));
        }
        var variants = List.of(
                request(TrustedAgentActor.verified(22, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/tenants/21"), RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE),
                request(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://other.example/tenants/21"), RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE),
                request(ACTOR, "00000000-0000-0000-0000-000000000124", "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE),
                request(ACTOR, RUN, "other", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE),
                request(ACTOR, RUN, "definition", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE),
                request(ACTOR, RUN, "definition", 1, "d".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE),
                request(ACTOR, RUN, "definition", 1, "a".repeat(64), "d".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE),
                request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "d".repeat(64), "policy-v1", true, DEADLINE),
                request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v2", true, DEADLINE),
                request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE),
                request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", true, DEADLINE.plusNanos(1000)));
        for (var changed : variants) assertNotEquals(digest, AgentServiceExecutionGrant.fingerprint(changed, RESOURCE, SUBJECT));
        assertNotEquals(digest, AgentServiceExecutionGrant.fingerprint(original, URI.create("https://other.example/tianji"), SUBJECT));
    }

    @Test void user_legacy_mismatched_service_and_unsafe_uris_cannot_form_a_service_grant() {
        for (var actor : List.of(new TrustedAgentActor(21, TrustedAgentActor.SubjectType.SERVICE, "worker"),
                TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "worker", "https://idp.example/tenants/21"),
                TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "other", "https://idp.example/tenants/21"))) {
            assertThrows(IllegalArgumentException.class, () -> AgentServiceExecutionGrant.fingerprint(request(actor, RUN, "definition", 1,
                    "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE), RESOURCE, SUBJECT));
        }
        var original = request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE);
        assertThrows(IllegalArgumentException.class, () -> AgentServiceExecutionGrant.fingerprint(original, URI.create("http://api.example/tianji"), SUBJECT));
        assertThrows(IllegalArgumentException.class, () -> AgentServiceExecutionGrant.fingerprint(original, URI.create("https://api.example/a/../tianji"), SUBJECT));
        assertThrows(IllegalArgumentException.class, () -> subject("worker", "process", "platform", "process", "test", URI.create("https://api.example/process?x=1")));
        assertThrows(IllegalArgumentException.class, () -> AgentServiceExecutionGrant.fingerprint(request(ACTOR, "not-a-uuid", "definition", 1,
                "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE), RESOURCE, SUBJECT));
    }

    @Test void ticket_and_reservation_are_bounded_redacted_and_separate_from_user_evidence() {
        var request = request(ACTOR, RUN, "definition", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE);
        String digest = AgentServiceExecutionGrant.fingerprint(request, RESOURCE, SUBJECT);
        var ticket = new AgentServiceExecutionGrant.Ticket("8" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE);
        var reservation = new AgentServiceExecutionGrant.Reservation(request, RESOURCE, SUBJECT, 1, digest);
        assertFalse(ticket.toString().contains("sealed"));
        assertFalse(reservation.toString().contains("worker"));
        assertFalse(SUBJECT.toString().contains("worker"));
        assertThrows(IllegalArgumentException.class, () -> new AgentServiceExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new AgentExecutionGrant.Ticket("8" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new AgentServiceExecutionGrant.Ticket("8" + "1".repeat(62), "not-sealed", DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new AgentServiceExecutionGrant.Ticket("8" + "1".repeat(62), "ENC:v1:key:nonce:" + "a".repeat(32_769), DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new AgentServiceExecutionGrant.Reservation(request, RESOURCE, SUBJECT, 0, digest));
        assertThrows(IllegalArgumentException.class, () -> new AgentServiceExecutionGrant.Reservation(request, RESOURCE, SUBJECT, 1, "f".repeat(64)));
    }

    private static AgentServiceExecutionGrant.Subject subject(String client, String app, String business, String application,
                                                               String environment, URI source) {
        return new AgentServiceExecutionGrant.Subject(client, app, business, application, environment, source);
    }

    private static AgentRunAuthorization.Request request(TrustedAgentActor actor, String run, String key, long version,
            String definition, String input, String snapshot, String policy, boolean session, Instant deadline) {
        return new AgentRunAuthorization.Request(actor, run, key, version, definition, input, snapshot, policy, session, deadline);
    }
}
