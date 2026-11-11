package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.SERVICE;
import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.USER;

/** Internal candidate admission and one independent review on the owning ProcessDB. */
public final class PlatformReleaseCandidateReviewService {
    private final ProcessReleaseService releases;
    private final PlatformReleaseCandidateReviewStore store;
    private final CurrentTrust trust;
    private final CurrentAuthorization authorization;

    public PlatformReleaseCandidateReviewService(SpringProcessEngineConfiguration engine, ProcessReleaseService releases,
                                                 CurrentTrust trust, CurrentAuthorization authorization) {
        this(releases, new PlatformReleaseCandidateReviewStore(engine, releases), trust, authorization);
    }

    PlatformReleaseCandidateReviewService(ProcessReleaseService releases, PlatformReleaseCandidateReviewStore store,
                                          CurrentTrust trust, CurrentAuthorization authorization) {
        this.releases = Objects.requireNonNull(releases);
        this.store = Objects.requireNonNull(store);
        this.trust = Objects.requireNonNull(trust);
        this.authorization = Objects.requireNonNull(authorization);
    }

    public SubmissionReceipt submit(ProcessActor actor, Scope scope, SignedMaterial material) {
        requireEntry(actor, scope);
        if (actor.subjectType() != SERVICE) throw denied();
        Objects.requireNonNull(material);
        var admission = authorize(new Authorization(actor, scope, Action.SUBMIT, null, null, null));
        var prior = store.findCandidate(scope);
        if (prior.isPresent()) return submissionReplay(actor, scope, material, prior.orElseThrow());

        var trusted = loadTrust(scope);
        var signature = verify(scope, material, trusted);
        var owner = observeProcess(actor, scope, signature);
        var receipt = new SubmissionReceipt(scope, actor, signature.envelopeSha(),
                PlatformReleaseArtifactValidator.hash(material.sidecar()), signature.revision(), signature.releaseVersion(),
                signature.manifestSha(), signature.issuedAt(), signature.expiresAt(), trusted.reference(),
                admission.auditSha256(), owner.grant().auditSha256(), owner.observedAt(), null);
        var result = store.appendCandidate(new CandidateRecord(receipt, material),
                admissionWindow(signature, trusted, owner, admission));
        if (result.created()) return result.value().submission();
        return submissionReplay(actor, scope, material, result.value());
    }

    public ReviewReceipt review(ProcessActor actor, Scope scope, ReviewCommand command) {
        requireEntry(actor, scope);
        if (actor.subjectType() != USER) throw denied();
        Objects.requireNonNull(command);
        var admission = authorize(new Authorization(actor, scope, Action.REVIEW, null, null, null));
        var previous = store.findReviewCommand(scope, actor, command.commandId());
        if (previous.isPresent()) return reviewReplay(actor, scope, command, previous.orElseThrow());
        var existing = store.findReview(scope);
        if (existing.isPresent()) return reviewReplay(actor, scope, command, existing.orElseThrow());

        var candidate = store.findCandidate(scope).orElseThrow(ProcessResourceNotFoundException::new);
        var submission = candidate.submission();
        var trusted = loadTrust(scope);
        requireStoredTrust(scope, submission, trusted);
        if (actor.equals(trusted.reference().signer())) throw denied();
        var independent = authorize(new Authorization(actor, scope, Action.REVIEW_INDEPENDENT,
                submission.releaseVersion(), trusted.reference().signer(), submission.submittedBy()));
        var signature = verifyStoredCandidate(scope, candidate, trusted);
        var owner = observeProcess(actor, scope, signature);
        var receipt = new ReviewReceipt(scope, actor, command, admission.auditSha256(), independent.auditSha256(),
                owner.grant().auditSha256(), owner.observedAt(), null);
        var result = store.appendReview(receipt, admissionWindow(signature, trusted, owner, admission, independent));
        if (result.created()) return result.value();
        return reviewReplay(actor, scope, command, result.value());
    }

    /** The read action independently authorizes inspecting signed material and the historical review. */
    public CandidateView get(ProcessActor actor, Scope scope) {
        requireEntry(actor, scope);
        var grant = authorizeRead(actor, scope);
        var candidate = store.findCandidate(scope).orElseThrow(ProcessResourceNotFoundException::new);
        var review = store.findReview(scope);
        requireCurrent(grant, store.now());
        return new CandidateView(candidate.submission(), candidate.material(), review);
    }

    ActivationVerification revalidateForActivation(ProcessActor actor, Scope scope) {
        requireEntry(actor, scope);
        if (actor.subjectType() != USER) throw denied();
        var read = authorizeRead(actor, scope);
        var candidate = store.findCandidate(scope).orElseThrow(ProcessResourceNotFoundException::new);
        var review = store.findReview(scope).orElseThrow(ProcessResourceNotFoundException::new);
        if (!scope.equals(review.scope()) || review.command().decision() != Decision.ACCEPT || review.decidedAt() == null) throw denied();

        var trusted = loadTrust(scope);
        requireStoredTrust(scope, candidate.submission(), trusted);
        var signature = verifyStoredCandidate(scope, candidate, trusted);
        var grant = authorize(new Authorization(actor, scope, Action.READ_PROCESS_RELEASE, signature.releaseVersion(), null, null));
        var release = releases.getForActivation(actor, signature.releaseVersion(), signature.deployment().link());
        var owner = observeProcess(grant, signature, release);
        var window = admissionWindow(signature, trusted, owner, read);
        if (!window.contains(owner.observedAt())) throw denied();
        return new ActivationVerification(signature, window, trusted.reference().generation());
    }

    private SubmissionReceipt submissionReplay(ProcessActor actor, Scope scope, SignedMaterial material, CandidateRecord prior) {
        authorizeRead(actor, scope);
        if (!actor.equals(prior.submission().submittedBy())
                || !Arrays.equals(material.envelope(), prior.material().envelope())
                || !Arrays.equals(material.sidecar(), prior.material().sidecar())) throw new ProcessConflictException();
        return prior.submission();
    }

    private ReviewReceipt reviewReplay(ProcessActor actor, Scope scope, ReviewCommand command, ReviewReceipt prior) {
        authorizeRead(actor, scope);
        if (!actor.equals(prior.reviewer()) || !command.commandId().equals(prior.command().commandId())) {
            throw new ProcessVersionConflictException();
        }
        if (!scope.equals(prior.scope()) || !command.equals(prior.command())) throw new ProcessConflictException();
        return prior;
    }

    private TrustLease loadTrust(Scope scope) {
        var lease = trust.current(scope);
        Instant now = store.now();
        if (lease == null || !lease.scope().equals(scope)
                || lease.observedAt().isAfter(now) || !now.isBefore(lease.validUntil())) throw denied();
        return lease;
    }

    private PlatformReleaseSignatureVerifier.VerifiedCandidate verify(Scope scope, SignedMaterial material, TrustLease trusted) {
        var verified = PlatformReleaseSignatureVerifier.verifyBytes(material.envelope(), material.sidecar(), trusted.descriptor(),
                scope.candidateSha256(), trusted.reference().descriptorSha256(), scope.tenantId(), scope.channel(),
                Clock.fixed(store.now(), ZoneOffset.UTC));
        if (!trusted.reference().keyId().equals(verified.keyId())) throw denied();
        return verified;
    }

    private static void requireStoredTrust(Scope scope, SubmissionReceipt submission, TrustLease trusted) {
        if (!scope.equals(submission.scope()) || !submission.trust().equals(trusted.reference())) throw denied();
    }

    private PlatformReleaseSignatureVerifier.VerifiedCandidate verifyStoredCandidate(Scope scope, CandidateRecord candidate,
                                                                                    TrustLease trusted) {
        var submission = candidate.submission();
        var signature = verify(scope, candidate.material(), trusted);
        if (!submission.envelopeSha256().equals(signature.envelopeSha())
                || !submission.sidecarSha256().equals(PlatformReleaseArtifactValidator.hash(candidate.material().sidecar()))
                || !submission.sourceRevision().equals(signature.revision())
                || !submission.signatureIssuedAt().equals(signature.issuedAt())
                || !submission.signatureExpiresAt().equals(signature.expiresAt())
                || !submission.releaseVersion().equals(signature.releaseVersion())
                || !submission.manifestSha256().equals(signature.manifestSha())) throw denied();
        return signature;
    }

    private OwnerObservation observeProcess(ProcessActor actor, Scope scope,
                                             PlatformReleaseSignatureVerifier.VerifiedCandidate signature) {
        var grant = authorize(new Authorization(actor, scope, Action.READ_PROCESS_RELEASE, signature.releaseVersion(), null, null));
        return observeProcess(grant, signature, releases.get(actor, signature.releaseVersion()));
    }

    private OwnerObservation observeProcess(Grant grant, PlatformReleaseSignatureVerifier.VerifiedCandidate signature,
                                             ProcessReleaseService.Release release) {
        var models = release.definitions().stream().map(definition -> new ProcessModelDTO(
                ProcessModelTypeEnum.valueOf(definition.type().name()), definition.definitionKey(), definition.modelSha256(),
                definition.inputs().entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> ProcessValueTypeEnum.valueOf(entry.getValue().name()))))).toList();
        Instant observedAt = store.now();
        requireCurrent(grant, observedAt);
        if (signature.processModels().size() != models.size()
                || !signature.matchesProcess(release.releaseKey(), release.manifestSha256(), models)) throw denied();
        return new OwnerObservation(grant, observedAt);
    }

    private Grant authorizeRead(ProcessActor actor, Scope scope) {
        return authorize(new Authorization(actor, scope, Action.READ_CANDIDATE, null, null, null));
    }

    private Grant authorize(Authorization request) {
        var grant = authorization.current(request);
        if (grant == null || !request.equals(grant.request())) throw denied();
        requireCurrent(grant, store.now());
        return grant;
    }

    private static void requireCurrent(Grant grant, Instant now) {
        if (grant.observedAt().isAfter(now) || !now.isBefore(grant.validUntil())) throw denied();
    }

    private static AdmissionWindow admissionWindow(PlatformReleaseSignatureVerifier.VerifiedCandidate signature,
                                                   TrustLease lease, OwnerObservation owner, Grant... grants) {
        var window = new AdmissionWindow(signature.issuedAt(), signature.expiresAt())
                .intersect(lease.observedAt(), lease.validUntil())
                .intersect(owner.observedAt(), owner.grant().validUntil());
        for (var grant : grants) window = window.intersect(grant.observedAt(), grant.validUntil());
        return window;
    }

    private static void requireEntry(ProcessActor actor, Scope scope) {
        if (actor == null || scope == null || actor.tenantId() != scope.tenantId() || !verified(actor)) throw denied();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Candidate review requires an independent transaction boundary");
        }
    }

    private static boolean verified(ProcessActor actor) { return !"LEGACY".equals(actor.issuerFingerprint()); }

    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static boolean channel(String value) { return value != null && value.matches("[a-z][a-z0-9._-]{0,63}"); }

    private static AccessDeniedException denied() { return new AccessDeniedException("Current candidate review authority is required"); }

    public enum Action { SUBMIT, REVIEW, REVIEW_INDEPENDENT, READ_CANDIDATE, READ_PROCESS_RELEASE }

    public enum Decision { ACCEPT, REJECT }

    public record Scope(long tenantId, String channel, String candidateSha256) {
        public Scope {
            if (tenantId <= 0 || !PlatformReleaseCandidateReviewService.channel(channel) || !digest(candidateSha256)) {
                throw new IllegalArgumentException("Candidate scope is invalid");
            }
        }
    }

    public record SignedMaterial(byte[] envelope, byte[] sidecar) {
        public SignedMaterial {
            if (envelope == null || envelope.length == 0 || envelope.length > 1_048_576
                    || sidecar == null || sidecar.length == 0 || sidecar.length > 8_192) {
                throw new IllegalArgumentException("Candidate signed material is invalid");
            }
            envelope = envelope.clone();
            sidecar = sidecar.clone();
        }
        @Override public byte[] envelope() { return envelope.clone(); }
        @Override public byte[] sidecar() { return sidecar.clone(); }
    }

    public record ReviewCommand(String commandId, int expectedRevision, Decision decision) {
        public ReviewCommand {
            if (commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                    || expectedRevision != 0 || decision == null) throw new IllegalArgumentException("Candidate review command is invalid");
        }
    }

    public record Authorization(ProcessActor actor, Scope scope, Action action, String releaseVersion,
                                ProcessActor signer, ProcessActor submitter) { }

    public record Grant(Authorization request, String auditSha256, Instant observedAt, Instant validUntil) {
        public Grant {
            if (request == null || !digest(auditSha256) || observedAt == null || validUntil == null
                    || !validUntil.isAfter(observedAt)) throw new IllegalArgumentException("Candidate authorization proof is invalid");
        }
    }

    @FunctionalInterface
    public interface CurrentAuthorization {
        /** REVIEW_INDEPENDENT must attest separation from signer and submitting service responsibility, including aliases. */
        Grant current(Authorization request);
    }

    public record TrustReference(long generation, String descriptorSha256, String keyId, ProcessActor signer) {
        public TrustReference {
            if (generation <= 0 || !digest(descriptorSha256) || !digest(keyId) || signer == null
                    || signer.subjectType() != USER || !verified(signer)) throw new IllegalArgumentException("Candidate trust reference is invalid");
        }
    }

    public record TrustLease(Scope scope, TrustReference reference, byte[] descriptor, Instant observedAt, Instant validUntil) {
        public TrustLease {
            if (scope == null || reference == null || reference.signer().tenantId() != scope.tenantId()
                    || descriptor == null || descriptor.length == 0 || descriptor.length > 8_192
                    || observedAt == null || validUntil == null || !validUntil.isAfter(observedAt)) {
                throw new IllegalArgumentException("Candidate trust lease is invalid");
            }
            descriptor = descriptor.clone();
        }
        @Override public byte[] descriptor() { return descriptor.clone(); }
    }

    @FunctionalInterface
    public interface CurrentTrust {
        /** Select the current release-purpose descriptor independently of the submitted envelope and sidecar. */
        TrustLease current(Scope scope);
    }

    public record SubmissionReceipt(Scope scope, ProcessActor submittedBy, String envelopeSha256, String sidecarSha256,
                                    String sourceRevision, String releaseVersion, String manifestSha256,
                                    Instant signatureIssuedAt, Instant signatureExpiresAt, TrustReference trust,
                                    String submissionAuditSha256, String ownerReadAuditSha256, Instant ownerObservedAt,
                                    Instant submittedAt) { }

    public record ReviewReceipt(Scope scope, ProcessActor reviewer, ReviewCommand command, String reviewAuditSha256,
                                String independenceAuditSha256, String ownerReadAuditSha256, Instant ownerObservedAt,
                                Instant decidedAt) {
        public int revision() { return 1; }
    }

    public record CandidateView(SubmissionReceipt submission, SignedMaterial material, Optional<ReviewReceipt> review) { }

    record ActivationVerification(PlatformReleaseSignatureVerifier.VerifiedCandidate signature, AdmissionWindow window,
                                  long trustGeneration) { }

    record CandidateRecord(SubmissionReceipt submission, SignedMaterial material) { }

    record Append<T>(T value, boolean created) { }

    record AdmissionWindow(Instant notBefore, Instant expiresAt) {
        AdmissionWindow intersect(Instant start, Instant end) {
            return new AdmissionWindow(start.isAfter(notBefore) ? start : notBefore, end.isBefore(expiresAt) ? end : expiresAt);
        }

        boolean contains(Instant now) { return !now.isBefore(notBefore) && now.isBefore(expiresAt); }
    }

    private record OwnerObservation(Grant grant, Instant observedAt) { }
}
