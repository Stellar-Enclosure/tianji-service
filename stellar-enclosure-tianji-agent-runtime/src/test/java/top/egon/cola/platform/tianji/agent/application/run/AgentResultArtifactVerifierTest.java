package top.egon.cola.platform.tianji.agent.application.run;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO.DecisionEnum;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactQuery.PurposeEnum;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentResultArtifactRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResultArtifactVerifierTest {
    private static final Instant NOW = Instant.parse("2026-11-14T09:20:00.123456Z");
    private static final Instant DEADLINE = NOW.plusSeconds(4);
    private static final String SCHEMA = "1".repeat(64);
    private static final String CONTENT = "2".repeat(64);
    private static final String AUTHORITY = "tenant-metadata";
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE,
            "process-main", "https://idp.example/21");
    private static final AgentResultArtifactRefVO ARTIFACT =
            new AgentResultArtifactRefVO("review-summary:one", CONTENT, "application/json", 510);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void canonical_query_binds_purpose_owner_route_run_schema_artifact_and_deadline() {
        var query = query();
        byte[] canonical = ProcessTypedJson.canonicalBytes(Map.of(
                "purpose", "PREPARE_TERMINAL",
                "owner", Map.of("tenantId", 21L, "subjectType", "SERVICE",
                        "issuerFingerprint", OWNER.issuerFingerprint(), "subjectId", OWNER.subjectId()),
                "sourceId", "ps-main", "producerId", "as-main", "destinationId", "process-main",
                "runId", "00000000-0000-0000-0000-000000000021", "nodeExecutionRef", "nx-101",
                "outputSchemaSha256", SCHEMA, "artifactRef", Map.of("resourceKey", "review-summary:one",
                        "contentSha256", CONTENT, "mediaType", "application/json", "sizeBytes", 510L),
                "deadlineAt", DEADLINE.toString()));
        String expected = ProcessTypedJson.sha256("tianji-agent-artifact-query-v2", canonical);
        assertEquals(expected, query.querySha256());

        var changed = List.of(query().setPurpose(PurposeEnum.DELIVER_TERMINAL), query().setSourceId("ps-other"),
                query().setProducerId("as-other"), query().setDestinationId("process-other"),
                query().setRunId("run-other"), query().setNodeExecutionRef("nx-other"),
                query().setOutputSchemaSha256("a".repeat(64)), query().setDeadlineAt(DEADLINE.plusSeconds(1)),
                query().setOwner(TrustedAgentActor.verified(22, TrustedAgentActor.SubjectType.SERVICE, "process-main", "https://idp.example/21")),
                query().setOwner(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "process-main", "https://idp.example/other")),
                query().setOwner(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "process-other", "https://idp.example/21")),
                query().setArtifactRef(new AgentResultArtifactRefVO("review-summary:two", CONTENT, "application/json", 510)),
                query().setArtifactRef(new AgentResultArtifactRefVO(ARTIFACT.resourceKey(), "c".repeat(64), "application/json", 510)),
                query().setArtifactRef(new AgentResultArtifactRefVO(ARTIFACT.resourceKey(), CONTENT, "text/plain", 510)),
                query().setArtifactRef(new AgentResultArtifactRefVO(ARTIFACT.resourceKey(), CONTENT, "application/json", 511)));
        changed.forEach(candidate -> assertNotEquals(expected, candidate.querySha256()));
    }

    @Test
    void fixed_metadata_provider_allows_only_its_verified_query_and_reads_no_body() {
        var expected = query();
        var observations = new AtomicInteger();
        var verifier = AgentResultArtifactVerifier.guarded(incoming -> {
            observations.incrementAndGet();
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (!expected.getOwner().equals(incoming.getOwner()) || !expected.getArtifactRef().equals(incoming.getArtifactRef())
                    || !expected.getRunId().equals(incoming.getRunId()) || !expected.getNodeExecutionRef().equals(incoming.getNodeExecutionRef())
                    || !expected.getOutputSchemaSha256().equals(incoming.getOutputSchemaSha256())
                    || !expected.getSourceId().equals(incoming.getSourceId()) || !expected.getProducerId().equals(incoming.getProducerId())
                    || !expected.getDestinationId().equals(incoming.getDestinationId())) return negative(DecisionEnum.DENY);
            return allow(incoming, NOW, NOW.plusSeconds(1));
        }, CLOCK);
        assertTrue(verifier.verify(expected).permits(expected, NOW));
        var delivery = query().setPurpose(PurposeEnum.DELIVER_TERMINAL);
        assertTrue(verifier.verify(delivery).permits(delivery, NOW));
        for (var denied : List.of(query().setSourceId("unknown-source"), query().setProducerId("other-producer"),
                query().setDestinationId("other-destination"), query().setRunId("run-other"), query().setNodeExecutionRef("nx-other"),
                query().setOutputSchemaSha256(CONTENT),
                query().setOwner(TrustedAgentActor.verified(22, TrustedAgentActor.SubjectType.SERVICE, "process-main", "https://idp.example/22")),
                query().setArtifactRef(new AgentResultArtifactRefVO(ARTIFACT.resourceKey(), CONTENT, "application/json", 511)))) {
            assertEquals(negative(DecisionEnum.DENY), verifier.verify(denied));
        }
        assertEquals(10, observations.get());
    }

    @Test
    void malformed_expired_and_transactional_queries_do_not_reach_authority() {
        var calls = new AtomicInteger();
        var verifier = AgentResultArtifactVerifier.guarded(incoming -> {
            calls.incrementAndGet();
            return allow(incoming, NOW, NOW.plusSeconds(1));
        }, CLOCK);
        var user = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "process-main", "https://idp.example/21");
        for (var invalid : List.of(new AgentResultArtifactQuery(), query().setOwner(user), query().setDestinationId("https://process/main"),
                query().setSourceId(" leading"), query().setRunId("x".repeat(65)), query().setNodeExecutionRef("/node"),
                query().setOutputSchemaSha256("BAD"), query().setDeadlineAt(NOW), query().setDeadlineAt(NOW.minusSeconds(1)),
                query().setDeadlineAt(DEADLINE.plusNanos(1)))) {
            assertEquals(negative(DecisionEnum.UNAVAILABLE), verifier.verify(invalid));
        }
        assertEquals(negative(DecisionEnum.UNAVAILABLE), verifier.verify(null));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(negative(DecisionEnum.UNAVAILABLE), verifier.verify(query()));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertEquals(0, calls.get());
    }

    @Test
    void absent_missing_unavailable_and_explicit_denied_have_no_allow_fallback() {
        assertEquals(negative(DecisionEnum.UNAVAILABLE), AgentResultArtifactVerifier.guarded(null, CLOCK).verify(query()));
        assertEquals(negative(DecisionEnum.UNAVAILABLE), AgentResultArtifactVerifier.guarded(ignored -> null, CLOCK).verify(query()));
        assertEquals(negative(DecisionEnum.UNAVAILABLE), AgentResultArtifactVerifier.guarded(ignored -> {
            throw new IllegalStateException("404: ordinary metadata lookup miss");
        }, CLOCK).verify(query()));
        assertEquals(negative(DecisionEnum.DENY),
                AgentResultArtifactVerifier.guarded(ignored -> negative(DecisionEnum.DENY), CLOCK).verify(query()));
    }

    @Test
    void evidence_rejects_wrong_digest_and_respects_current_clock_and_exclusive_expiration() {
        var query = query();
        var evidence = allow(query, NOW, NOW.plusSeconds(1));
        assertTrue(evidence.permits(query, NOW));
        assertFalse(evidence.permits(query, NOW.minusNanos(1)));
        assertFalse(evidence.permits(query, NOW.plusSeconds(1)));
        assertFalse(evidence.permits(query().setPurpose(PurposeEnum.DELIVER_TERMINAL), NOW));
        for (var invalid : List.of(allow(query, NOW.minusSeconds(1), NOW), allow(query, NOW.plusSeconds(1), NOW.plusSeconds(2)),
                allow(query, NOW, DEADLINE.plusSeconds(1)), allow(query().setDestinationId("other-destination"), NOW, NOW.plusSeconds(1)))) {
            assertEquals(negative(DecisionEnum.UNAVAILABLE), AgentResultArtifactVerifier.guarded(ignored -> invalid, CLOCK).verify(query));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResultArtifactEvidenceVO(DecisionEnum.DENY, AUTHORITY, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResultArtifactEvidenceVO(DecisionEnum.ALLOW, "https://authority.example", query.querySha256(), NOW, DEADLINE));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResultArtifactEvidenceVO(DecisionEnum.ALLOW, AUTHORITY, query.querySha256(), NOW.plusNanos(1), DEADLINE));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResultArtifactEvidenceVO(DecisionEnum.ALLOW, AUTHORITY, query.querySha256(), NOW, NOW));
    }

    @Test
    void provider_cannot_change_original_scope_or_return_an_allow_after_deadline() {
        var original = query();
        var verifier = AgentResultArtifactVerifier.guarded(incoming -> {
            incoming.setDestinationId("other-destination");
            return allow(incoming, NOW, NOW.plusSeconds(1));
        }, CLOCK);
        assertEquals(negative(DecisionEnum.UNAVAILABLE), verifier.verify(original));
        assertEquals("process-main", original.getDestinationId());
        var clock = new ControlledClock();
        var afterDeadline = AgentResultArtifactVerifier.guarded(incoming -> {
            clock.current.set(DEADLINE);
            return allow(incoming, NOW, DEADLINE);
        }, clock);
        assertEquals(negative(DecisionEnum.UNAVAILABLE), afterDeadline.verify(original));
    }

    @Test
    void timeout_keeps_capacity_owned_until_an_uninterruptible_authority_really_exits() throws Exception {
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(8);
        var released = new CountDownLatch(1);
        var inherited = new InheritableThreadLocal<String>();
        inherited.set("caller-private");
        var verifier = AgentResultArtifactVerifier.guarded(incoming -> {
            assertNull(inherited.get());
            calls.incrementAndGet();
            entered.countDown();
            while (released.getCount() > 0) {
                try {
                    released.await();
                } catch (InterruptedException ignored) {
                    // Exercise a provider that does not return on cancellation.
                }
            }
            return allow(incoming, NOW, NOW.plusSeconds(1));
        }, CLOCK);

        try (var requesters = Executors.newVirtualThreadPerTaskExecutor()) {
            var timed = new ArrayList<Future<AgentResultArtifactEvidenceVO>>();
            for (int i = 0; i < 8; i++) {
                timed.add(requesters.submit(() -> verifier.verify(query().setDeadlineAt(NOW.plusSeconds(1)))));
            }
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                for (var result : timed) {
                    assertEquals(negative(DecisionEnum.UNAVAILABLE), result.get(3, TimeUnit.SECONDS));
                }
                assertEquals(negative(DecisionEnum.UNAVAILABLE), verifier.verify(query()));
                assertEquals(8, calls.get());
            } finally {
                released.countDown();
            }
        } finally {
            inherited.remove();
            ((AutoCloseable) verifier).close();
        }
    }

    private static AgentResultArtifactEvidenceVO allow(AgentResultArtifactQuery query, Instant checkedAt, Instant validUntil) {
        return new AgentResultArtifactEvidenceVO(DecisionEnum.ALLOW, AUTHORITY, query.querySha256(), checkedAt, validUntil);
    }

    private static AgentResultArtifactEvidenceVO negative(DecisionEnum decision) {
        return new AgentResultArtifactEvidenceVO(decision, null, null, null, null);
    }

    private static AgentResultArtifactQuery query() {
        return new AgentResultArtifactQuery(PurposeEnum.PREPARE_TERMINAL, OWNER, "ps-main", "as-main", "process-main",
                "00000000-0000-0000-0000-000000000021", "nx-101", SCHEMA, ARTIFACT, DEADLINE);
    }

    private static final class ControlledClock extends Clock {
        private final AtomicReference<Instant> current = new AtomicReference<>(NOW);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
