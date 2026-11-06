package top.egon.cola.platform.tianji.process.server.release;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.SERVICE;
import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.USER;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.*;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.CANDIDATE;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.CANDIDATE_PIN;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.SIDECAR;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.TRUST;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.TRUST_PIN;

final class PlatformReleaseCandidateReviewServiceTest {
    private static final Scope SCOPE = new Scope(20, "expense", CANDIDATE_PIN);
    private static final String ISSUER = "https://process.example.test";
    private static final ProcessActor SUBMITTER = ProcessActor.verified(20, SERVICE, "publisher", ISSUER);
    private static final ProcessActor SIGNER = ProcessActor.verified(20, USER, "signer", ISSUER);
    private static final ProcessActor REVIEWER = ProcessActor.verified(20, USER, "reviewer", ISSUER);
    private static final ProcessActor OTHER_REVIEWER = ProcessActor.verified(20, USER, "reviewer", "https://second.example.test");
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private static final Instant AUTH_DEADLINE = NOW.plusSeconds(15);
    private static final SignedMaterial SIGNED = new SignedMaterial(CANDIDATE.getBytes(UTF_8), SIDECAR.getBytes(UTF_8));
    private static final ReviewCommand ACCEPT = new ReviewCommand("review-1", 0, Decision.ACCEPT);
    private static final PlatformReleaseSignatureVerifier.VerifiedCandidate PROOF = PlatformReleaseSignatureVerifier.verifyBytes(
            SIGNED.envelope(), SIGNED.sidecar(), TRUST.getBytes(UTF_8), CANDIDATE_PIN, TRUST_PIN, 20, "expense",
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void signed_candidate_and_independent_review_preserve_audited_facts() {
        var fixture = new Fixture();
        var submission = fixture.service.submit(SUBMITTER, SCOPE, SIGNED);
        assertEquals(SUBMITTER, submission.submittedBy());
        assertEquals(SIGNER, submission.trust().signer());
        assertEquals(NOW, submission.submittedAt());
        assertEquals(PROOF.manifestSha(), submission.manifestSha256());

        var review = fixture.service.review(REVIEWER, SCOPE, ACCEPT);
        assertEquals(REVIEWER, review.reviewer());
        assertEquals(1, review.revision());
        assertEquals(ACCEPT, review.command());
        assertEquals(NOW, review.decidedAt());
        assertEquals(new Authorization(REVIEWER, SCOPE, Action.REVIEW_INDEPENDENT,
                "expense-v1", SIGNER, SUBMITTER), fixture.independent.get());
        assertEquals(new AdmissionWindow(NOW, AUTH_DEADLINE), fixture.window.get());
        assertEquals(review, fixture.service.get(REVIEWER, SCOPE).review().orElseThrow());
        verify(fixture.releases).get(SUBMITTER, "expense-v1");
        verify(fixture.releases).get(REVIEWER, "expense-v1");
    }

    @Test
    void owner_mismatch_and_signature_failure_cannot_create_a_candidate() {
        var fixture = new Fixture();
        var sameVersion = ownerRelease();
        when(fixture.releases.get(SUBMITTER, "expense-v1")).thenReturn(new ProcessReleaseService.Release(
                sameVersion.releaseKey(), "0".repeat(64), sameVersion.createdAt(), sameVersion.definitions()));
        assertThrows(AccessDeniedException.class, () -> fixture.service.submit(SUBMITTER, SCOPE, SIGNED));
        var changedSidecar = SIDECAR.replace(PROOF.keyId(), "0".repeat(64)).getBytes(UTF_8);
        assertThrows(PlatformReleaseSignatureVerifier.SignatureFailure.class,
                () -> fixture.service.submit(SUBMITTER, SCOPE, new SignedMaterial(SIGNED.envelope(), changedSidecar)));
        verify(fixture.store, never()).appendCandidate(any(), any());
    }

    @Test
    void wrong_tenant_kind_and_legacy_are_denied_before_authority_or_storage() {
        var fixture = new Fixture();
        var crossTenant = new Scope(21, "expense", CANDIDATE_PIN);
        assertThrows(AccessDeniedException.class, () -> fixture.service.submit(SUBMITTER, crossTenant, SIGNED));
        assertThrows(AccessDeniedException.class, () -> fixture.service.submit(REVIEWER, SCOPE, SIGNED));
        assertThrows(AccessDeniedException.class, () -> fixture.service.review(SUBMITTER, SCOPE, ACCEPT));
        assertThrows(AccessDeniedException.class,
                () -> fixture.service.submit(new ProcessActor(20, SERVICE, "publisher"), SCOPE, SIGNED));
        verifyNoInteractions(fixture.store, fixture.releases);
    }

    @Test
    void current_trust_and_independence_are_required_for_every_new_review() {
        var fixture = new Fixture();
        fixture.service.submit(SUBMITTER, SCOPE, SIGNED);
        assertThrows(AccessDeniedException.class, () -> fixture.service.review(SIGNER, SCOPE, ACCEPT));
        fixture.refused.add(Action.REVIEW_INDEPENDENT);
        assertThrows(AccessDeniedException.class, () -> fixture.service.review(REVIEWER, SCOPE, ACCEPT));
        fixture.refused.clear();

        fixture.trust.set(lease(2, NOW.plusSeconds(60)));
        assertThrows(AccessDeniedException.class, () -> fixture.service.review(REVIEWER, SCOPE, ACCEPT));
        fixture.trust.set(lease(1, NOW));
        assertThrows(AccessDeniedException.class, () -> fixture.service.review(REVIEWER, SCOPE, ACCEPT));
        verify(fixture.store, never()).appendReview(any(), any());
    }

    @Test
    void historical_replay_requires_independent_read_and_never_rewrites() {
        var fixture = new Fixture();
        var submission = fixture.service.submit(SUBMITTER, SCOPE, SIGNED);
        var review = fixture.service.review(REVIEWER, SCOPE, ACCEPT);
        fixture.trust.set(null);

        assertEquals(submission, fixture.service.submit(SUBMITTER, SCOPE, SIGNED));
        assertEquals(review, fixture.service.review(REVIEWER, SCOPE, ACCEPT));
        assertThrows(ProcessConflictException.class, () -> fixture.service.review(REVIEWER, SCOPE,
                new ReviewCommand(ACCEPT.commandId(), 0, Decision.REJECT)));
        assertThrows(ProcessVersionConflictException.class, () -> fixture.service.review(OTHER_REVIEWER, SCOPE, ACCEPT));

        fixture.refused.add(Action.READ_CANDIDATE);
        assertThrows(AccessDeniedException.class, () -> fixture.service.submit(SUBMITTER, SCOPE, SIGNED));
        assertThrows(AccessDeniedException.class, () -> fixture.service.review(REVIEWER, SCOPE, ACCEPT));
        verify(fixture.store).appendCandidate(any(), any());
        verify(fixture.store).appendReview(any(), any());
    }

    @Test
    void conflicting_insert_and_late_lookup_use_exact_replay_rules() {
        var fixture = new Fixture();
        fixture.service.submit(SUBMITTER, SCOPE, SIGNED);
        var winning = new ReviewReceipt(SCOPE, REVIEWER, ACCEPT, "a".repeat(64), "a".repeat(64), "a".repeat(64), NOW, NOW);
        doReturn(new Append<>(winning, false)).when(fixture.store).appendReview(any(), any());
        assertEquals(winning, fixture.service.review(REVIEWER, SCOPE, ACCEPT));
        assertThrows(ProcessVersionConflictException.class, () -> fixture.service.review(OTHER_REVIEWER, SCOPE, ACCEPT));

        when(fixture.store.findReview(SCOPE)).thenReturn(Optional.of(winning));
        assertEquals(winning, fixture.service.review(REVIEWER, SCOPE, ACCEPT));
        assertTrue(fixture.window.get().contains(NOW));
    }

    private static TrustLease lease(long generation, Instant expiry) {
        return new TrustLease(SCOPE, new TrustReference(generation, TRUST_PIN, PROOF.keyId(), SIGNER),
                TRUST.getBytes(UTF_8), NOW.minusSeconds(1), expiry);
    }

    private static ProcessReleaseService.Release ownerRelease() {
        var definitions = PROOF.processModels().stream().map(model -> new ProcessReleaseService.Definition(
                ValidatedProcessModel.Type.valueOf(model.type().name()), model.definitionKey(), "engine-definition",
                "engine-deployment", 1, model.contentSha256(), model.inputs().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey,
                        entry -> ValidatedProcessModel.ValueType.valueOf(entry.getValue().name()))))).toList();
        return new ProcessReleaseService.Release(PROOF.releaseVersion(), PROOF.manifestSha(), NOW.minusSeconds(1), definitions);
    }

    private static final class Fixture {
        private final ProcessReleaseService releases = mock(ProcessReleaseService.class);
        private final PlatformReleaseCandidateReviewStore store = mock(PlatformReleaseCandidateReviewStore.class);
        private final AtomicReference<TrustLease> trust = new AtomicReference<>(lease(1, NOW.plusSeconds(60)));
        private final AtomicReference<CandidateRecord> candidate = new AtomicReference<>();
        private final AtomicReference<ReviewReceipt> review = new AtomicReference<>();
        private final AtomicReference<AdmissionWindow> window = new AtomicReference<>();
        private final AtomicReference<Authorization> independent = new AtomicReference<>();
        private final EnumSet<Action> refused = EnumSet.noneOf(Action.class);
        private final PlatformReleaseCandidateReviewService service = new PlatformReleaseCandidateReviewService(releases, store,
                scope -> trust.get(), request -> {
                    if (refused.contains(request.action())) return null;
                    if (request.action() == Action.REVIEW_INDEPENDENT) independent.set(request);
                    return new Grant(request, "a".repeat(64), NOW.minusSeconds(2), AUTH_DEADLINE);
                });

        private Fixture() {
            when(store.now()).thenReturn(NOW);
            when(releases.get(any(), eq("expense-v1"))).thenReturn(ownerRelease());
            when(store.findCandidate(SCOPE)).thenAnswer(ignored -> Optional.ofNullable(candidate.get()));
            when(store.findReview(SCOPE)).thenAnswer(ignored -> Optional.ofNullable(review.get()));
            when(store.findReviewCommand(eq(SCOPE), any(), any())).thenAnswer(call -> Optional.ofNullable(review.get())
                    .filter(receipt -> receipt.reviewer().equals(call.getArgument(1))
                            && receipt.command().commandId().equals(call.getArgument(2))));
            when(store.appendCandidate(any(), any())).thenAnswer(call -> {
                CandidateRecord proposed = call.getArgument(0);
                var facts = proposed.submission();
                var stored = new SubmissionReceipt(facts.scope(), facts.submittedBy(), facts.envelopeSha256(), facts.sidecarSha256(),
                        facts.sourceRevision(), facts.releaseVersion(), facts.manifestSha256(), facts.signatureIssuedAt(),
                        facts.signatureExpiresAt(), facts.trust(), facts.submissionAuditSha256(), facts.ownerReadAuditSha256(),
                        facts.ownerObservedAt(), NOW);
                candidate.set(new CandidateRecord(stored, proposed.material()));
                window.set(call.getArgument(1));
                return new Append<>(candidate.get(), true);
            });
            when(store.appendReview(any(), any())).thenAnswer(call -> {
                ReviewReceipt proposed = call.getArgument(0);
                review.set(new ReviewReceipt(proposed.scope(), proposed.reviewer(), proposed.command(), proposed.reviewAuditSha256(),
                        proposed.independenceAuditSha256(), proposed.ownerReadAuditSha256(), proposed.ownerObservedAt(), NOW));
                window.set(call.getArgument(1));
                return new Append<>(review.get(), true);
            });
        }
    }
}
