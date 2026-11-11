package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.CurrentAuthorization;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.CurrentTrust;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Scope;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Applies a reviewed release to the tenant's governed new-start pointer. Requires explicit owner authorities. */
public final class PlatformReleaseActivationService {
    private static final Duration MAX_PROOF_WINDOW = Duration.ofSeconds(30);
    private final PlatformReleaseCandidateReviewService candidates;
    private final PlatformReleaseActivationStore store;
    private final ActivationAuthorization authorization;
    private final CurrentDeploymentEvidence deployments;

    public PlatformReleaseActivationService(SpringProcessEngineConfiguration engine, ProcessReleaseService releases,
                                            CurrentTrust trust, CurrentAuthorization candidateAuthorization,
                                            ActivationAuthorization authorization, CurrentDeploymentEvidence deployments) {
        this(new PlatformReleaseCandidateReviewService(engine, releases, trust, candidateAuthorization),
                new PlatformReleaseActivationStore(engine, releases), authorization, deployments);
    }

    PlatformReleaseActivationService(PlatformReleaseCandidateReviewService candidates, PlatformReleaseActivationStore store,
                                     ActivationAuthorization authorization, CurrentDeploymentEvidence deployments) {
        this.candidates = Objects.requireNonNull(candidates);
        this.store = Objects.requireNonNull(store);
        this.authorization = Objects.requireNonNull(authorization);
        this.deployments = Objects.requireNonNull(deployments);
    }

    public ActivationReceipt change(ProcessActor actor, ChangeCommand command) {
        if (command == null || !verifiedUser(actor, command.target())) throw denied();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Release activation requires an independent transaction boundary");
        }
        var request = new AuthorizationRequest(actor, command);
        var grant = authorization.current(request);
        if (grant == null || !request.equals(grant.request())) throw denied();
        requireCurrent(grant.observedAt(), grant.validUntil(), store.now());

        var previous = store.findCommand(command.target(), actor, command.commandId());
        if (previous.isPresent()) {
            var receipt = previous.orElseThrow();
            requireCurrent(grant.observedAt(), grant.validUntil(), store.now());
            if (!command.equals(receipt.command())) throw new ProcessConflictException();
            return receipt;
        }

        var target = candidates.revalidateForActivation(actor, command.target());
        var signature = target.signature();
        var declared = signature.deployment();
        var link = declared.link();
        var model = signature.processModels().stream()
                .filter(entry -> entry.type() == ProcessModelTypeEnum.BPMN && entry.definitionKey().equals(link.processDefinitionKey()))
                .findFirst().orElseThrow(PlatformReleaseActivationService::denied);
        var query = new DeploymentQuery(command.target(), signature.releaseVersion(), link.processDefinitionKey(),
                link.topic(), link.schemaVersion(), declared.agent().definitionKey(), declared.agent().definitionVersion());
        var proof = deployments.current(query);
        if (proof == null || !query.equals(proof.query()) || !declared.equals(proof.observedManifest())) throw denied();
        requireCurrent(proof.observedAt(), proof.validUntil(), store.now());
        requireBinding(command.target(), declared, proof.worker());

        var window = target.window().intersect(grant.observedAt(), grant.validUntil())
                .intersect(proof.observedAt(), proof.validUntil());
        var proposed = new ActivationReceipt(command, actor, null, signature.releaseVersion(), signature.manifestSha(),
                link.processDefinitionKey(), model.contentSha256(), link.topic(), link.schemaVersion(),
                workerFingerprint(proof.worker(), proof.workerRegistrationSha256()), grant.auditSha256(), proof.auditSha256(),
                target.trustGeneration(), null);
        return store.append(proposed, window);
    }

    private static void requireBinding(Scope scope, DeploymentManifest manifest, AgentDispatchBinding binding) {
        var agent = manifest.agent();
        var link = manifest.link();
        if (binding.actor().tenantId() != scope.tenantId() || !binding.definitionKey().equals(agent.definitionKey())
                || binding.definitionVersion() != agent.definitionVersion()
                || !binding.definitionContentSha256().equals(agent.definitionContentSha256())
                || !binding.topic().equals(link.topic()) || !binding.schemaVersion().equals(link.schemaVersion())) throw denied();
    }

    public static String workerFingerprint(AgentDispatchBinding worker, String registrationSha256) {
        return OrchestrationRequestDigests.hash(List.of("TIANJI_ACTIVATION_WORKER_V1",
                Long.toString(worker.actor().tenantId()), worker.actor().issuerFingerprint(), worker.actor().subjectId(),
                worker.sourceId(), worker.topic(), worker.schemaVersion(), worker.definitionKey(),
                Long.toString(worker.definitionVersion()), worker.definitionContentSha256(), worker.instruction(),
                worker.submissionTimeout().toString(), registrationSha256), Map.of());
    }

    private static void requireCurrent(Instant observedAt, Instant validUntil, Instant now) {
        if (observedAt.isAfter(now) || !now.isBefore(validUntil)
                || Duration.between(observedAt, validUntil).compareTo(MAX_PROOF_WINDOW) > 0) throw denied();
    }

    private static boolean verifiedUser(ProcessActor actor, Scope scope) {
        return actor != null && actor.tenantId() == scope.tenantId() && actor.subjectType() == ProcessActor.SubjectType.USER
                && !"LEGACY".equals(actor.issuerFingerprint());
    }

    private static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static boolean ref(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit;
    }

    private static boolean validWindow(Instant observedAt, Instant validUntil) {
        return observedAt != null && validUntil != null && validUntil.isAfter(observedAt);
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Current verified release activation authority is required");
    }

    public enum Action { ACTIVATE, ROLLBACK }

    public record ChangeCommand(Scope target, String commandId, long expectedRevision, Action action) {
        public ChangeCommand {
            if (target == null || commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                    || expectedRevision < 0 || expectedRevision == Long.MAX_VALUE || action == null) {
                throw new IllegalArgumentException("Release activation command is invalid");
            }
        }
    }

    public record AuthorizationRequest(ProcessActor actor, ChangeCommand command) { }

    public record ActivationGrant(AuthorizationRequest request, String auditSha256, Instant observedAt, Instant validUntil) {
        public ActivationGrant {
            if (request == null || !digest(auditSha256) || !validWindow(observedAt, validUntil)) {
                throw new IllegalArgumentException("Release activation grant is invalid");
            }
        }
    }

    @FunctionalInterface
    public interface ActivationAuthorization {
        /** Check current permission for the exact USER, tenant, channel, operation, revision and candidate. */
        ActivationGrant current(AuthorizationRequest request);
    }

    public record DeploymentQuery(Scope target, String processReleaseVersion, String processDefinitionKey,
                                  String workerTopic, String workerSchemaVersion, String agentDefinitionKey,
                                  long agentDefinitionVersion) { }

    public record ContractRef(String version, String artifactSha256) {
        public ContractRef {
            if (!ref(version, 64) || !digest(artifactSha256)) throw new IllegalArgumentException("Contract reference is invalid");
        }
    }

    public record AgentPublication(String definitionKey, long definitionVersion, String definitionArtifactSha256,
                                   String definitionContentSha256, String modelRouteRef, String policyVersionRef,
                                   ContractRef inputContract, ContractRef outputContract) {
        public AgentPublication {
            if (!ref(definitionKey, 128) || definitionVersion <= 0 || !digest(definitionArtifactSha256)
                    || !digest(definitionContentSha256) || !ref(modelRouteRef, 256) || !ref(policyVersionRef, 256)
                    || inputContract == null || outputContract == null) throw new IllegalArgumentException("Agent publication is invalid");
        }
    }

    public record ProcessLink(String processDefinitionKey, String workerElementId, String topic, String schemaVersion) {
        public ProcessLink {
            if (!ref(processDefinitionKey, 128) || !ref(workerElementId, 128)) {
                throw new IllegalArgumentException("Process link is invalid");
            }
            ProcessModelValidator.nativeWorkerTopic(topic, schemaVersion);
        }
    }

    public record ArtifactRef(String archiveSha256, String manifestEntrySha256) {
        public ArtifactRef {
            if (!digest(archiveSha256) || !digest(manifestEntrySha256)) throw new IllegalArgumentException("Artifact identity is invalid");
        }
    }

    public record DeploymentManifest(AgentPublication agent, ProcessLink link, ArtifactRef agentPackage, ArtifactRef processPackage) {
        public DeploymentManifest {
            Objects.requireNonNull(agent);
            Objects.requireNonNull(link);
            Objects.requireNonNull(agentPackage);
            Objects.requireNonNull(processPackage);
        }
    }

    public record DeploymentProof(DeploymentQuery query, DeploymentManifest observedManifest, AgentDispatchBinding worker,
                                  String workerRegistrationSha256, String auditSha256, Instant observedAt, Instant validUntil) {
        public DeploymentProof {
            if (query == null || observedManifest == null || worker == null || !digest(workerRegistrationSha256)
                    || !digest(auditSha256) || !validWindow(observedAt, validUntil)) {
                throw new IllegalArgumentException("Release deployment evidence is invalid");
            }
        }
    }

    @FunctionalInterface
    public interface CurrentDeploymentEvidence {
        /**
         * Read independently from the current owning deployments and authorization registries. Verify the published
         * CONFIG definition, empty optional dependency sets, model and policy versions, deployed package manifests
         * and effective Worker registration and routing, including the ability to retain existing routes.
         */
        DeploymentProof current(DeploymentQuery query);
    }

    public record ActivationReceipt(ChangeCommand command, ProcessActor actor, String previousCandidateSha256,
                                    String processReleaseVersion, String processManifestSha256, String processDefinitionKey,
                                    String processModelSha256, String workerTopic, String workerSchemaVersion,
                                    String workerBindingSha256, String authorizationSha256, String dependencyEvidenceSha256,
                                    long trustGeneration, Instant activatedAt) {
        public ActivationReceipt {
            if (command == null || !verifiedUser(actor, command.target())
                    || previousCandidateSha256 != null && !digest(previousCandidateSha256)
                    || !ref(processReleaseVersion, 128) || !ref(processDefinitionKey, 128)
                    || !digest(processManifestSha256) || !digest(processModelSha256) || !digest(workerBindingSha256)
                    || !digest(authorizationSha256) || !digest(dependencyEvidenceSha256) || trustGeneration <= 0
                    || activatedAt != null && (command.expectedRevision() == 0) != (previousCandidateSha256 == null)
                    || activatedAt == null && previousCandidateSha256 != null) {
                throw new IllegalArgumentException("Release activation receipt is invalid");
            }
            ProcessModelValidator.nativeWorkerTopic(workerTopic, workerSchemaVersion);
        }

        public long revision() { return command.expectedRevision() + 1; }
    }
}
