package top.egon.cola.platform.tianji.process.server.release;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.Action;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ActivationGrant;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ActivationReceipt;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.AgentPublication;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ArtifactRef;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.AuthorizationRequest;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ChangeCommand;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.DeploymentManifest;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.DeploymentProof;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.DeploymentQuery;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.AdmissionWindow;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Scope;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.CANDIDATE;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.CANDIDATE_PIN;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.SIDECAR;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.TRUST;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.TRUST_PIN;

class PlatformReleaseActivationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final String ISSUER = "https://process.example.test";
    private static final ProcessActor USER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "release-operator", ISSUER);
    private static final ProcessActor BRIDGE = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-worker", ISSUER);
    private static final Scope SCOPE = new Scope(20, "expense", CANDIDATE_PIN);
    private static final ChangeCommand ACTIVATE = new ChangeCommand(SCOPE, "activate-1", 0, Action.ACTIVATE);

    @Test
    void verified_release_and_independent_deployment_are_pinned_in_the_proposal() {
        var fixture = new Fixture();
        var receipt = fixture.service.change(USER, ACTIVATE);
        assertEquals(ACTIVATE, receipt.command());
        assertEquals("expenseReview", receipt.processDefinitionKey());
        assertEquals("5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6", receipt.processModelSha256());
        assertEquals("f65fc8ac24ff2683ab3c3645e4fb60f7003666380767014df51bea10d96b24f5", receipt.processManifestSha256());
        assertEquals("agentStart", receipt.workerTopic());
        assertEquals(4, receipt.trustGeneration());
        assertEquals("a".repeat(64), receipt.authorizationSha256());
        assertEquals("b".repeat(64), receipt.dependencyEvidenceSha256());
        verify(fixture.store).append(receipt, new AdmissionWindow(NOW.minusSeconds(1), NOW.plusSeconds(3)));

        fixture.worker = new AgentDispatchBinding(BRIDGE, "release-bridge", "agentStart", "1",
                "expenseAnalysis", 1, "a".repeat(64), "Changed instruction.", Duration.ofSeconds(4));
        assertNotEquals(receipt.workerBindingSha256(), fixture.service.change(USER, ACTIVATE).workerBindingSha256());
    }

    @Test
    void rejects_a_current_deployment_with_different_agent_content_before_writing() {
        var fixture = new Fixture();
        var manifest = fixture.manifest;
        var agent = manifest.agent();
        fixture.manifest = new DeploymentManifest(new AgentPublication(agent.definitionKey(), agent.definitionVersion(),
                agent.definitionArtifactSha256(), "e".repeat(64), agent.modelRouteRef(), agent.policyVersionRef(),
                agent.inputContract(), agent.outputContract()), manifest.link(), manifest.agentPackage(), manifest.processPackage());
        assertThrows(AccessDeniedException.class, () -> fixture.service.change(USER, ACTIVATE));
        verify(fixture.store, never()).append(any(), any());
    }

    @Test
    void rejects_wrong_package_expired_proof_or_changed_worker_definition() {
        var artifact = new Fixture();
        var manifest = artifact.manifest;
        artifact.manifest = new DeploymentManifest(manifest.agent(), manifest.link(),
                new ArtifactRef("f".repeat(64), manifest.agentPackage().manifestEntrySha256()), manifest.processPackage());
        assertThrows(AccessDeniedException.class, () -> artifact.service.change(USER, ACTIVATE));
        verify(artifact.store, never()).append(any(), any());

        var expired = new Fixture();
        expired.proofExpiresAt = NOW;
        assertThrows(AccessDeniedException.class, () -> expired.service.change(USER, ACTIVATE));
        verify(expired.store, never()).append(any(), any());

        var route = new Fixture();
        route.worker = new AgentDispatchBinding(BRIDGE, "release-bridge", "agentStart", "1",
                "expenseAnalysis", 2, "a".repeat(64), "Inspect expense.", Duration.ofSeconds(4));
        assertThrows(AccessDeniedException.class, () -> route.service.change(USER, ACTIVATE));
        verify(route.store, never()).append(any(), any());
    }

    @Test
    void rejects_legacy_foreign_and_overlong_or_unbound_authority() {
        var fixture = new Fixture();
        var legacy = new ProcessActor(20, ProcessActor.SubjectType.USER, "release-operator");
        var foreign = ProcessActor.verified(21, ProcessActor.SubjectType.USER, "release-operator", ISSUER);
        assertThrows(AccessDeniedException.class, () -> fixture.service.change(legacy, ACTIVATE));
        assertThrows(AccessDeniedException.class, () -> fixture.service.change(foreign, ACTIVATE));
        fixture.grantExpiresAt = NOW.plusSeconds(31);
        assertThrows(AccessDeniedException.class, () -> fixture.service.change(USER, ACTIVATE));

        var unbound = new Fixture();
        unbound.grantCommand = new ChangeCommand(SCOPE, "other", 0, Action.ACTIVATE);
        assertThrows(AccessDeniedException.class, () -> unbound.service.change(USER, ACTIVATE));
        verifyNoInteractions(fixture.candidates, unbound.candidates);
    }

    @Test
    void exact_historical_command_replays_under_current_authority_without_reopening_old_deployment() {
        var fixture = new Fixture();
        var receipt = new ActivationReceipt(ACTIVATE, USER, null, "r1", "1".repeat(64), "expenseReview",
                "2".repeat(64), "agentStart", "1", "3".repeat(64), "4".repeat(64), "5".repeat(64), 4, NOW);
        when(fixture.store.findCommand(SCOPE, USER, ACTIVATE.commandId())).thenReturn(Optional.of(receipt));
        assertSame(receipt, fixture.service.change(USER, ACTIVATE));
        var changed = new ChangeCommand(SCOPE, ACTIVATE.commandId(), 1, Action.ROLLBACK);
        assertThrows(ProcessConflictException.class, () -> fixture.service.change(USER, changed));
        verifyNoInteractions(fixture.candidates);
        verify(fixture.store, never()).append(any(), any());
    }

    private static final class Fixture {
        final PlatformReleaseCandidateReviewService candidates = mock(PlatformReleaseCandidateReviewService.class);
        final PlatformReleaseActivationStore store = mock(PlatformReleaseActivationStore.class);
        final PlatformReleaseActivationService service;
        DeploymentManifest manifest;
        AgentDispatchBinding worker = new AgentDispatchBinding(BRIDGE, "release-bridge", "agentStart", "1",
                "expenseAnalysis", 1, "a".repeat(64), "Inspect expense.", Duration.ofSeconds(4));
        Instant proofExpiresAt = NOW.plusSeconds(3);
        Instant grantExpiresAt = NOW.plusSeconds(5);
        ChangeCommand grantCommand;

        Fixture() {
            var signed = PlatformReleaseSignatureVerifier.verifyBytes(CANDIDATE.getBytes(StandardCharsets.UTF_8),
                    SIDECAR.getBytes(StandardCharsets.UTF_8), TRUST.getBytes(StandardCharsets.UTF_8),
                    CANDIDATE_PIN, TRUST_PIN, SCOPE.tenantId(), SCOPE.channel(), Clock.fixed(NOW, ZoneOffset.UTC));
            manifest = signed.deployment();
            when(candidates.revalidateForActivation(USER, SCOPE))
                    .thenReturn(new PlatformReleaseCandidateReviewService.ActivationVerification(signed,
                            new AdmissionWindow(NOW.minusSeconds(2), NOW.plusSeconds(8)), 4));
            when(store.now()).thenReturn(NOW);
            when(store.append(any(), any())).thenAnswer(call -> call.getArgument(0));
            service = new PlatformReleaseActivationService(candidates, store,
                    request -> new ActivationGrant(grantCommand == null ? request : new AuthorizationRequest(request.actor(), grantCommand),
                            "a".repeat(64), NOW.minusSeconds(1), grantExpiresAt),
                    this::observe);
        }

        private DeploymentProof observe(DeploymentQuery query) {
            return new DeploymentProof(query, manifest, worker, "c".repeat(64), "b".repeat(64),
                    NOW.minusSeconds(1), proofExpiresAt);
        }
    }
}
