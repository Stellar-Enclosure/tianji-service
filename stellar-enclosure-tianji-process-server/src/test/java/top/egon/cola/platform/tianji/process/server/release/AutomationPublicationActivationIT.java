package top.egon.cola.platform.tianji.process.server.release;

import org.flywaydb.core.Flyway;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.DependencySnapshotVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.TemplateRegistrationVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.VersionedRefVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.WorkerRegistrationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.AutomationActivationDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftMutationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftPublicationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ValidateProcessDraftQuery;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import org.springframework.security.access.AccessDeniedException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftCommandReceiptBO;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftConverter;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated("Owns one expressly provisioned empty Process database")
@Timeout(150)
class AutomationPublicationActivationIT {
    private static final String SCHEMA = "tianji_process";
    private static final String ENABLED = "stellar.tianji.process.automation.enabled";
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "editor", "https://idp.test/main");
    private static final ProcessActor COPIER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "other-editor", "https://idp.test/main");
    private static final ProcessActor OTHER_TENANT = ProcessActor.verified(21, ProcessActor.SubjectType.USER, "editor", "https://idp.test/main");
    private static final String XML = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:step20">
              <process id="review" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="done" sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;
    private static final String OBJECT = "{ \"type\": \"object\", \"properties\": {}, \"required\": [], \"additionalProperties\": false }";
    private static final DraftContentVO COMPLETE = new DraftContentVO(List.of(new DraftModelSourceVO("BPMN", "review", XML)),
            OBJECT, OBJECT, " [ ] ", "[]");

    @Test
    void seals_one_exact_revision_and_original_receipt_without_activation_or_v1_admission() throws Exception {
        var db = provision();
        var environment = new MockEnvironment().withProperty(ENABLED, "true");
        try (var first = context(db.url(), db.role(), false); var reopened = context(db.url(), db.role(), false)) {
            var components = components(first, environment);
            var again = components(reopened, environment);
            var original = components.drafts().create(OWNER, "review", new CreateProcessDraftCommand("Review", null, "initial"));
            var incomplete = components.drafts().save(OWNER, original.getDraftId(),
                    new SaveProcessDraftCommand("broken", 1, "Incomplete", new DraftContentVO(null, "{", null, null, null)));
            assertFalse(components.drafts().validate(OWNER, preview(incomplete)).isValid());
            assertThrows(IllegalArgumentException.class, () -> publish(components.drafts(), OWNER, command(incomplete, "blocked", "bad")));
            var saved = components.drafts().save(OWNER, original.getDraftId(), new SaveProcessDraftCommand("ready", 2, "Ready", COMPLETE));
            assertThrows(AutomationOperationException.class, () -> components.drafts().validate(OWNER, preview(incomplete)));
            assertThrows(AutomationOperationException.class, () -> publish(components.drafts(), OWNER, command(incomplete, "stale", "stale")));
            var validated = components.drafts().validate(OWNER, preview(saved));
            assertTrue(validated.isValid());
            assertEquals(List.of("START", "END"), validated.getGraph().nodes().stream().map(node -> node.kind()).toList());
            assertEquals(0, count(db, "act_re_deployment"));

            var start = new CountDownLatch(1);
            var entered = new CountDownLatch(2);
            var leftCommand = command(saved, "automation-one", "race-left");
            var rightCommand = command(saved, "automation-two", "race-right");
            DraftPublicationVO winner;
            PublishProcessDraftCommand winningCommand;
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                var left = pool.submit(() -> race(components.drafts(), leftCommand, entered, start));
                var right = pool.submit(() -> race(again.drafts(), rightCommand, entered, start));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                start.countDown();
                var firstResult = left.get(35, TimeUnit.SECONDS);
                var secondResult = right.get(35, TimeUnit.SECONDS);
                assertTrue((firstResult == null) != (secondResult == null));
                winner = firstResult == null ? secondResult : firstResult;
                winningCommand = firstResult == null ? rightCommand : leftCommand;
            }
            assertEquals(1, count(db, "act_re_deployment"));
            assertEquals(1, count(db, "tianji_process_release"));
            assertEquals(1, count(db, "tianji_process_automation_release"));
            assertEquals(0, count(db, "tianji_process_automation_activation"));
            assertEquals(0, count(db, "tianji_platform_release_activation"));
            assertEquals(0, count(db, "act_ru_execution"));
            assertEquals(saved.getRevision(), winner.publishedRevision());
            assertEquals(saved.getRevision() + 1, winner.draftRevision());
            assertEquals(validated.getSourceManifestSha256(), winner.sourceManifestSha256());
            var receipt = again.drafts().get(OWNER, original.getDraftId(), winner.commandId());
            assertEquals(winner, receipt.getPublication());
            assertEquals(winner.draftRevision(), receipt.getCommandReceipt().getResultRevision());
            assertEquals(winner.releaseVersion(), receipt.getCommandReceipt().getReleaseVersion());

            environment.setProperty(ENABLED, "false");
            assertEquals(winner, publish(again.drafts(), OWNER, winningCommand));
            var immutable = again.drafts().getAutomationRelease(COPIER, winner.releaseVersion());
            assertEquals(XML, immutable.getArtifact().models().getFirst().xml());
            assertEquals(validated.getGraph(), immutable.getGraph());
            assertThrows(ProcessResourceNotFoundException.class, () -> again.drafts().getAutomationRelease(OTHER_TENANT, winner.releaseVersion()));
            assertThrows(ProcessResourceNotFoundException.class, () -> again.releases().get(OWNER, winner.releaseVersion()));
            rejectsLegacyStartForCompiledRelease(db, reopened, winner.releaseVersion());
            assertThrows(ProcessResourceNotFoundException.class, () -> publish(again.drafts(), COPIER, winningCommand));
            assertThrows(ProcessConflictException.class, () -> publish(again.drafts(), OWNER,
                    command(saved, "different-version", winner.commandId())));
            environment.setProperty(ENABLED, "true");
            var copy = again.drafts().create(COPIER, "review", new CreateProcessDraftCommand("Copied", winner.releaseVersion(), "copy-made"));
            var editable = again.drafts().get(COPIER, copy.getDraftId(), null);
            assertEquals(winner.sourceManifestSha256(), editable.getBaseSourceManifestSha256());
            assertEquals(XML, editable.getContent().models().getFirst().xml());
            assertNotEquals(OBJECT, editable.getContent().inputSchemaSource());
            assertThrows(ProcessResourceNotFoundException.class, () -> again.drafts().get(COPIER, original.getDraftId(), null));
            assertThrows(ProcessResourceNotFoundException.class, () -> again.drafts().create(OTHER_TENANT, "review",
                    new CreateProcessDraftCommand("Hidden copy", winner.releaseVersion(), "cross-tenant-copy")));
            assertThrows(IllegalArgumentException.class, () -> again.drafts().create(COPIER, "wrong-root",
                    new CreateProcessDraftCommand("Wrong root", winner.releaseVersion(), "wrong-root-copy")));
            assertEquals(1, count(db, "act_re_deployment"));

            var legacy = components.releases().publish(OWNER, "legacy", List.of(new ProcessReleaseService.ModelResource(
                    ValidatedProcessModel.Type.BPMN, "review", XML.getBytes(StandardCharsets.UTF_8), Map.of())));
            assertEquals(legacy, again.releases().get(OWNER, "legacy"));
            var sourceUnavailable = assertThrows(AutomationOperationException.class, () -> again.drafts().create(COPIER, "review",
                    new CreateProcessDraftCommand("Legacy copy", "legacy", "legacy-copy")));
            assertEquals(AutomationOperationException.Code.TIANJI_DRAFT_SOURCE_UNAVAILABLE, sourceUnavailable.code());
            assertThrows(ProcessResourceNotFoundException.class, () -> again.drafts().getAutomationRelease(COPIER, "legacy"));
            assertEquals(2, count(db, "act_re_deployment"));

            var pending = components.drafts().create(OWNER, "review", new CreateProcessDraftCommand("Rollback", null, "rollback-draft"));
            var ready = components.drafts().save(OWNER, pending.getDraftId(), new SaveProcessDraftCommand("rollback-ready", 1, "Ready", COMPLETE));
            var failingCommand = command(ready, "rollback-target", "forced-failure");
            var injection = new AtomicBoolean(true);
            var faulty = service(first, components.releases(), environment, injection);
            assertThrows(IllegalStateException.class, () -> publish(faulty, OWNER, failingCommand));
            assertFalse(injection.get(), "receipt fault must be reached after native publication and sealing");
            assertEquals(2, count(db, "act_re_deployment"));
            assertEquals(2, count(db, "tianji_process_release"));
            assertEquals(1, count(db, "tianji_process_automation_release"));
            assertEquals(0, count(db, "tianji_process_automation_activation"));
            assertEquals(0, count(db, "act_ru_execution"));
            assertEquals(ready.getRevision(), again.drafts().get(OWNER, pending.getDraftId(), "forced-failure").getRevision());
            assertNull(again.drafts().get(OWNER, pending.getDraftId(), "forced-failure").getCommandReceipt());
            var retried = publish(again.drafts(), OWNER, failingCommand);
            assertEquals("rollback-target", retried.releaseVersion());
            assertEquals(3, count(db, "act_re_deployment"));

            var late = components.drafts().create(OWNER, "review", new CreateProcessDraftCommand("Concurrent retry", null, "late-draft"));
            var last = components.drafts().save(OWNER, late.getDraftId(), new SaveProcessDraftCommand("late-ready", 1, "Ready", COMPLETE));
            var matching = command(last, "late-target", "late-same-command");
            assertThrows(AuthenticationCredentialsNotFoundException.class,
                    () -> again.drafts().publish(OWNER, matching, Instant.EPOCH));
            assertNull(again.drafts().get(OWNER, late.getDraftId(), "late-same-command").getCommandReceipt());

            // A dependency outage after a preflight miss must give a winning identical command's original result.
            var resolving = new CountDownLatch(1);
            var finish = new CountDownLatch(1);
            var unavailable = service(first, components.releases(), environment, new AtomicBoolean(), (actor, source) -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                resolving.countDown();
                try {
                    assertTrue(finish.await(20, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                throw new IllegalStateException("Test dependency outage");
            });
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                var waiter = pool.submit(() -> publish(unavailable, OWNER, matching));
                try {
                    assertTrue(resolving.await(10, TimeUnit.SECONDS));
                    var committed = publish(again.drafts(), OWNER, matching);
                    finish.countDown();
                    assertEquals(committed, waiter.get(20, TimeUnit.SECONDS));
                    assertThrows(AuthenticationCredentialsNotFoundException.class,
                            () -> again.drafts().publish(OWNER, matching, Instant.EPOCH));
                } finally {
                    finish.countDown();
                }
            }
            assertEquals(4, count(db, "act_re_deployment"));
            assertEquals(0, count(db, "tianji_process_automation_activation"));
            verifiedWorkerDependencyControlsFirstPublication(db, first, reopened, components, again, environment);
            publishedDecisionRetainsItsOwnExactDeployment(db, reopened, components, again);
            verifiesIndependentActivationAndFrozenHistory(db, first, reopened, components, again, environment, winner.releaseVersion());
        }
    }

    @Test
    void resolvesRealNamedServiceMapperAndOnlyNamedHostAuthorities() {
        var activationStore = mock(AutomationActivationStore.class);
        var releaseStore = mock(AutomationReleaseStore.class);
        var release = new ImmutableReleaseVO(OWNER.tenantId(), "wiring-v2", "review", null,
                "a".repeat(64), AutomationReleaseStore.compilerVersion(), "b".repeat(64), Instant.EPOCH,
                "draft-wiring-v2", 1, "c".repeat(64), Map.of(), "d".repeat(64));
        when(releaseStore.find(OWNER.tenantId(), release.releaseVersion())).thenReturn(Optional.of(release));
        var unqualifiedUsage = new AtomicBoolean();
        var permissionUsage = new AtomicBoolean();
        var trustUsage = new AtomicBoolean();
        var testClock = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        when(activationStore.now()).thenReturn(testClock);
        var runner = new ApplicationContextRunner()
                .withPropertyValues(ENABLED + "=true")
                .withUserConfiguration(AutomationActivationService.class,
                        Mappers.getMapper(AutomationActivationConverter.class).getClass())
                .withBean("automationActivationStore", AutomationActivationStore.class, () -> activationStore)
                .withBean("automationReleaseStore", AutomationReleaseStore.class, () -> releaseStore)
                .withBean("processReleaseService", ProcessReleaseService.class, () -> mock(ProcessReleaseService.class))
                .withBean("processApiSchemaVerifier", ProcessApiSchemaVerifier.class, () -> mock(ProcessApiSchemaVerifier.class))
                .withBean("processDraftTransactionTemplate", TransactionTemplate.class,
                        () -> new TransactionTemplate(mock(PlatformTransactionManager.class)))
                .withBean("anotherAdmissionAuthority", AutomationAdmissionAuthority.class, () -> query -> {
                    unqualifiedUsage.set(true);
                    throw new AssertionError("Unnamed admission authority was used");
                })
                .withBean("anotherTrustAuthority", AutomationReleaseTrustVerifier.TrustAuthority.class, () -> (query, descriptor) -> {
                    unqualifiedUsage.set(true);
                    throw new AssertionError("Unnamed trust authority was used");
                });
        var command = new ActivationCommand(release.releaseVersion(), 0L, "activate-wiring");
        runner.run(app -> {
            assertNull(app.getStartupFailure());
            var service = app.getBean("automationActivationService", AutomationActivationService.class);
            var mapper = app.getBean("automationActivationConverter", AutomationActivationConverter.class);
            assertEquals("automationActivationConverter", mapper.getClass().getAnnotation(Component.class).value());
            assertThrows(IllegalStateException.class, () -> service.activate(OWNER, "review", command));
            assertFalse(unqualifiedUsage.get());
        });
        runner.withBean("automationAdmissionAuthority", AutomationAdmissionAuthority.class, () -> query -> {
            permissionUsage.set(true);
            return new AutomationAdmissionProofVO(query.sha256(), "fixture", "a".repeat(64), testClock,
                    testClock.plusSeconds(15), 1, "b".repeat(64), "c".repeat(64), "d".repeat(64), Map.of());
        }).withBean("automationReleaseTrustAuthority", AutomationReleaseTrustVerifier.TrustAuthority.class, () -> (query, descriptor) -> {
            trustUsage.set(true);
            return null;
        }).run(app -> {
            assertNull(app.getStartupFailure());
            var service = app.getBean("automationActivationService", AutomationActivationService.class);
            assertThrows(IllegalStateException.class, () -> service.activate(OWNER, "review", command));
            assertTrue(permissionUsage.get());
            assertTrue(trustUsage.get());
            assertFalse(unqualifiedUsage.get());
        });
    }

    private static void verifiesIndependentActivationAndFrozenHistory(
            TestDatabaseVO db, ConfigurableApplicationContext first, ConfigurableApplicationContext reopened,
            TestServicesVO components, TestServicesVO again, MockEnvironment environment, String releaseVersion) throws Exception {
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var allowReplay = new AtomicBoolean(true);
        var allowSigning = new AtomicBoolean(true);
        var expireProof = new AtomicBoolean();
        var signatureCalls = new AtomicInteger();
        var replayCalls = new AtomicInteger();
        var waitForRace = new AtomicBoolean();
        var fencedDeadline = new AtomicReference<Instant>();
        var observedBoth = new CountDownLatch(2);
        var ownerFromAnotherIssuer = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "editor", "https://idp.test/other");
        var signatures = new java.util.concurrent.ConcurrentHashMap<String, AutomationReleaseTrustVerifier.TrustObservationVO>();
        AutomationReleaseTrustVerifier.TrustAuthority signaturesByRelease = (query, release) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            signatureCalls.incrementAndGet();
            if (!allowSigning.get()) throw new IllegalStateException("Signing registry is offline");
            return signatures.get(query.getReleaseVersion());
        };
        AutomationAdmissionAuthority permission = query -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            Instant now = db.owner().queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class).toInstant();
            if (query.getPurpose() == AutomationAdmissionQuery.Purpose.REPLAY_ACTIVATION) {
                replayCalls.incrementAndGet();
                if (!allowReplay.get()) throw new AccessDeniedException("Denied historical permission");
                return new AutomationAdmissionProofVO(query.sha256(), "replay-auth", "a".repeat(64), now, now.plusSeconds(20),
                        1, "b".repeat(64), "c".repeat(64), "d".repeat(64), Map.of());
            }
            if (!OWNER.equals(query.getActor())) throw new AccessDeniedException("Independent activation permission denied");
            if (waitForRace.get()) {
                observedBoth.countDown();
                await(observedBoth);
            }
            var material = signatures.get(query.getReleaseVersion());
            Instant validUntil = now.plusSeconds(20);
            if ("activation-fenced-expiry".equals(query.getCommandId())) {
                validUntil = now.plusSeconds(4);
                fencedDeadline.set(validUntil);
            }
            return new AutomationAdmissionProofVO(query.sha256(), "activate-auth", "a".repeat(64),
                    expireProof.get() ? now.minusSeconds(20) : now, expireProof.get() ? now.minusSeconds(1) : validUntil,
                    material.key().generation(), material.envelopeSha256(), material.payloadSha256(),
                    material.dependencyEvidenceSha256(query), Map.of("authorizationVersion", "v1"));
        };
        var service = activation(first, components.releases(), environment, permission, signaturesByRelease);
        var restarted = activation(reopened, again.releases(), environment, permission, signaturesByRelease);
        var jdbc = new JdbcTemplate(((DataSourceTransactionManager) first.getBean(SpringProcessEngineConfiguration.class)
                .getTransactionManager()).getDataSource());
        var descriptors = new AutomationReleaseStore(jdbc, SCHEMA);
        publishActivationProfiles(first, components.releases(), environment);
        for (String version : List.of(releaseVersion, "rollback-target", "worker-release", "human-release", "two-agent-release")) {
            var release = descriptors.find(OWNER.tenantId(), version).orElseThrow();
            byte[] exact = AutomationReleaseTrustVerifier.canonicalPayload(release);
            Instant time = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            String keyId = PlatformReleaseArtifactValidator.hash(key.getPublic().getEncoded());
            var trusted = new AutomationReleaseTrustVerifier.TrustedKeyVO(keyId, OWNER.tenantId(), 1,
                    Base64.getEncoder().encodeToString(key.getPublic().getEncoded()), time.minusSeconds(60), time.plusSeconds(90));
            Instant issuedAt = time.minusSeconds(10);
            Instant expiresAt = time.plusSeconds(60);
            var signer = Signature.getInstance("Ed25519");
            signer.initSign(key.getPrivate());
            signer.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, issuedAt, expiresAt));
            var envelope = new AutomationReleaseTrustVerifier.SignedEnvelopeVO(keyId, issuedAt, expiresAt,
                    Base64.getEncoder().encodeToString(signer.sign()));
            signatures.put(version, new AutomationReleaseTrustVerifier.TrustObservationVO(trusted, 1, envelope,
                    release.dependencies(), AutomationReleaseTrustVerifier.payloadSha256(exact)));
        }
        assertNull(service.list(OWNER, "review", null, null, null).getCurrent());
        var initial = new ActivationCommand(releaseVersion, 0L, "activation-original");
        assertThrows(AccessDeniedException.class, () -> service.activate(COPIER, "review", initial));
        assertThrows(IllegalArgumentException.class, () -> service.activate(OWNER, "different", initial));
        assertThrows(ProcessResourceNotFoundException.class, () -> service.activate(OTHER_TENANT, "review", initial));
        assertThrows(ProcessResourceNotFoundException.class, () -> service.list(OTHER_TENANT, "review", null, null, null));
        expireProof.set(true);
        assertThrows(IllegalStateException.class, () -> service.activate(OWNER, "review", initial));
        expireProof.set(false);
        assertEquals(0, count(db, "tianji_process_automation_activation"));

        // Recompute every advertised digest around a forged envelope. Only actual Ed25519 verification rejects it.
        var originalMaterial = signatures.get(releaseVersion);
        var originalEnvelope = originalMaterial.envelope();
        signatures.put(releaseVersion, new AutomationReleaseTrustVerifier.TrustObservationVO(originalMaterial.key(), 1,
                new AutomationReleaseTrustVerifier.SignedEnvelopeVO(originalEnvelope.keyId(), originalEnvelope.issuedAt(),
                        originalEnvelope.expiresAt(), Base64.getEncoder().encodeToString(new byte[64])),
                originalMaterial.observedDependencies(), originalMaterial.payloadSha256()));
        assertThrows(IllegalStateException.class, () -> service.activate(OWNER, "review", initial));
        signatures.put(releaseVersion, new AutomationReleaseTrustVerifier.TrustObservationVO(originalMaterial.key(), 1,
                new AutomationReleaseTrustVerifier.SignedEnvelopeVO(originalEnvelope.keyId(), originalEnvelope.issuedAt(),
                        originalEnvelope.expiresAt().plusSeconds(10), originalEnvelope.signature()),
                originalMaterial.observedDependencies(), originalMaterial.payloadSha256()));
        assertThrows(IllegalStateException.class, () -> service.activate(OWNER, "review", initial));
        signatures.put(releaseVersion, originalMaterial);

        var firstResult = service.activate(OWNER, "review", initial);
        assertEquals(1, firstResult.getRevision());
        assertNull(firstResult.getPreviousRevision());
        assertEquals(initial.getReleaseVersion(), firstResult.getReleaseVersion());
        var manager = first.getBean(SpringProcessEngineConfiguration.class).getTransactionManager();
        var expiryCommand = new ActivationCommand(releaseVersion, 1L, "activation-fenced-expiry");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = new AtomicReference<java.util.concurrent.Future<AutomationActivationDTO>>();
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                PlatformReleaseTenantFence.acquireExclusive(jdbc, OWNER.tenantId());
                pending.set(pool.submit(() -> restarted.activate(OWNER, "review", expiryCommand)));
                awaitBlockedActivation(db);
                assertNotNull(fencedDeadline.get());
                awaitDatabaseDeadline(db, fencedDeadline.get());
            });
            var expired = assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get().get(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, expired.getCause());
            assertEquals("Automation admission proof is unavailable or expired", expired.getCause().getMessage());
        }
        assertEquals(1, count(db, "tianji_process_automation_activation"));
        waitForRace.set(true);
        var left = new ActivationCommand("rollback-target", 1L, "activation-left");
        var right = new ActivationCommand("worker-release", 1L, "activation-right");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var leftCall = pool.submit(() -> raceActivation(service, left));
            var rightCall = pool.submit(() -> raceActivation(restarted, right));
            var resultLeft = leftCall.get(20, TimeUnit.SECONDS);
            var resultRight = rightCall.get(20, TimeUnit.SECONDS);
            assertTrue((resultLeft == null) != (resultRight == null));
        }
        waitForRace.set(false);
        var initialPage = service.list(OWNER, "review", null, 1, null);
        assertEquals(2, initialPage.getCurrent().revision());
        assertEquals(2, initialPage.getItems().getFirst().getRevision());
        assertNotNull(initialPage.getNextCursor());
        assertEquals(3, service.activate(OWNER, "review", new ActivationCommand(initialPage.getCurrent().releaseVersion(),
                2L, "activation-explicit-again")).getRevision());
        var nextPage = restarted.list(OWNER, "review", null, 1, initialPage.getNextCursor());
        assertEquals(3, nextPage.getCurrent().revision());
        assertEquals(List.of(firstResult), nextPage.getItems());
        assertNull(nextPage.getNextCursor());
        assertEquals(List.of(firstResult), service.list(OWNER, "review", initial.getCommandId(), 20, null).getItems());
        assertTrue(service.list(ownerFromAnotherIssuer, "review", initial.getCommandId(), null, null).getItems().isEmpty());

        var human = service.activate(OWNER, "approval", new ActivationCommand("human-release", 0L, "activate-human"));
        assertEquals(1, human.getRevision());
        assertEquals("human-release", service.list(OWNER, "approval", null, null, null).getCurrent().releaseVersion());
        var multi = new ActivationCommand("two-agent-release", 0L, "activate-agents");
        var fullObservation = signatures.get("two-agent-release");
        var incompleteDependencies = new java.util.LinkedHashMap<>(fullObservation.observedDependencies());
        var templates = (Map<?, ?>) incompleteDependencies.get("agentTemplates");
        assertEquals(2, templates.size());
        var firstTemplate = templates.entrySet().iterator().next();
        incompleteDependencies.put("agentTemplates", Map.of(firstTemplate.getKey(), firstTemplate.getValue()));
        signatures.put("two-agent-release", new AutomationReleaseTrustVerifier.TrustObservationVO(fullObservation.key(),
                fullObservation.currentGeneration(), fullObservation.envelope(), incompleteDependencies, fullObservation.payloadSha256()));
        assertThrows(IllegalStateException.class, () -> service.activate(OWNER, "twoAgents", multi));
        assertNull(service.list(OWNER, "twoAgents", null, null, null).getCurrent());
        signatures.put("two-agent-release", fullObservation);
        assertEquals(1, restarted.activate(OWNER, "twoAgents", multi).getRevision());
        assertEquals("two-agent-release", service.list(OWNER, "twoAgents", null, null, null).getCurrent().releaseVersion());

        environment.setProperty(ENABLED, "false");
        allowSigning.set(false);
        int registryLookups = signatureCalls.get();
        assertEquals(firstResult, restarted.activate(OWNER, "review", initial));
        assertEquals(1, replayCalls.get());
        assertEquals(registryLookups, signatureCalls.get());
        assertThrows(ProcessConflictException.class, () -> restarted.activate(OWNER, "review",
                new ActivationCommand("rollback-target", 2L, initial.getCommandId())));
        allowReplay.set(false);
        assertThrows(AccessDeniedException.class, () -> restarted.activate(OWNER, "review", initial));
        assertEquals(5, count(db, "tianji_process_automation_activation"));
        assertEquals(0, count(db, "tianji_platform_release_activation"));
        assertEquals(0, count(db, "act_ru_execution"));
        assertEquals(3, restarted.list(OWNER, "review", null, null, null).getCurrent().revision());
    }

    private static void publishActivationProfiles(ConfigurableApplicationContext app, ProcessReleaseService releases,
                                                  MockEnvironment environment) {
        var empty = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(), "required", List.of(),
                "additionalProperties", false));
        var stop = new NodePolicyVO.PolicyActionVO("END", null);
        var policy = new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(30), stop, stop, stop,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        var human = new AutomationNodeBindingVO("approve", "HUMAN", empty, empty, List.of(), List.of(), policy,
                null, null, null, new AutomationNodeBindingVO.HumanBindingVO("approvers", empty, Duration.ofMinutes(1)), null, null);
        String humanXml = XML.replace("id=\"review\"", "id=\"approval\"").replace("<endEvent id=\"end\"/>", """
                <userTask id="approve"/>
                <endEvent id="end"/>
                <sequenceFlow id="approved" sourceRef="approve" targetRef="end"/>
                """).replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"approve\"");
        String agentXml = XML.replace("id=\"review\"", "id=\"twoAgents\"").replace("<endEvent id=\"end\"/>", """
                <serviceTask id="collect"/>
                <serviceTask id="verify"/>
                <endEvent id="end"/>
                <sequenceFlow id="collected" sourceRef="collect" targetRef="verify"/>
                <sequenceFlow id="verified" sourceRef="verify" targetRef="end"/>
                """).replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"collect\"");
        var collect = new TemplateRefVO("collector", 1, "a".repeat(64));
        var verify = new TemplateRefVO("verifier", 2, "b".repeat(64));
        String schemaSha = AutomationModelValidator.schemaSha256(empty);
        var registries = new DependencySnapshotVO(OWNER.tenantId(), Map.of(), Map.of(
                new VersionedRefVO(collect.key(), collect.version()), new TemplateRegistrationVO(collect, schemaSha, schemaSha, schemaSha),
                new VersionedRefVO(verify.key(), verify.version()), new TemplateRegistrationVO(verify, schemaSha, schemaSha, schemaSha)),
                Set.of(), Map.of("approvers", "c".repeat(64)), Map.of());
        var dependencyProvider = new AutomationReleaseStore.DependencyResolver() {
            @Override
            public DependencySnapshotVO resolve(ProcessActor actor, AutomationModelSourceVO source) { return registries; }

            @Override
            public boolean isCurrent(ProcessActor actor, AutomationModelSourceVO source, DependencySnapshotVO observation) {
                return actor.tenantId() == registries.tenantId() && observation == registries;
            }
        };
        var drafts = service(app, releases, environment, new AtomicBoolean(), dependencyProvider);
        publishProfile(drafts, "approval", "human-release", humanXml, empty, List.of(human));
        publishProfile(drafts, "twoAgents", "two-agent-release", agentXml, empty, List.of(
                agentNode("collect", collect, empty, policy), agentNode("verify", verify, empty, policy)));
    }

    private static AutomationNodeBindingVO agentNode(String id, TemplateRefVO template, ProcessSchemaVO empty, NodePolicyVO policy) {
        var agent = new AgentTaskBindingVO(template, List.of(template), empty, Map.of(), List.of(), null);
        return new AutomationNodeBindingVO(id, "AGENT", empty, empty, List.of(), List.of(), policy,
                null, null, agent, null, null, null);
    }

    private static void publishProfile(ProcessDraftService drafts, String definition, String version, String xml,
                                       ProcessSchemaVO empty, List<AutomationNodeBindingVO> nodes) {
        var source = new AutomationModelSourceVO("AUTOMATION_V2",
                List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", definition, xml)), empty, empty, nodes, List.of());
        var created = drafts.create(OWNER, definition, new CreateProcessDraftCommand(definition, null, "draft-" + version));
        var saved = drafts.save(OWNER, created.getDraftId(),
                new SaveProcessDraftCommand("save-" + version, 1, definition, AutomationReleaseStore.copyContent(source)));
        var validation = drafts.validate(OWNER, preview(saved));
        assertTrue(validation.isValid(), () -> validation.getIssues().toString());
        assertEquals(version, publish(drafts, OWNER, command(saved, version, "publish-" + version)).releaseVersion());
    }

    private static void awaitBlockedActivation(TestDatabaseVO db) {
        Instant timeout = Instant.now().plusSeconds(8);
        while (Instant.now().isBefore(timeout)) {
            if (Boolean.TRUE.equals(db.owner().queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_locks WHERE locktype='advisory' AND NOT granted
                      AND database=(SELECT oid FROM pg_database WHERE datname=current_database()))
                    """, Boolean.class))) return;
            pause();
        }
        fail("Activation did not wait on the tenant fence");
    }

    private static void awaitDatabaseDeadline(TestDatabaseVO db, Instant cutoff) {
        while (db.owner().queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class).toInstant().isBefore(cutoff)) {
            pause();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static AutomationActivationDTO raceActivation(AutomationActivationService service, ActivationCommand command) {
        try {
            return service.activate(OWNER, "review", command);
        } catch (ProcessVersionConflictException expected) {
            return null;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static AutomationActivationService activation(ConfigurableApplicationContext app, ProcessReleaseService releases,
            MockEnvironment environment, AutomationAdmissionAuthority authority, AutomationReleaseTrustVerifier.TrustAuthority trust) {
        var manager = (DataSourceTransactionManager) app.getBean(SpringProcessEngineConfiguration.class).getTransactionManager();
        var source = manager.getDataSource();
        var jdbc = new JdbcTemplate(source);
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var providers = new StaticListableBeanFactory(Map.of("authority", authority, "trust", trust));
        return new AutomationActivationService(new AutomationActivationStore(jdbc, SCHEMA), new AutomationReleaseStore(jdbc, SCHEMA),
                releases, Mappers.getMapper(AutomationActivationConverter.class), new ProcessApiSchemaVerifier(source, SCHEMA),
                transaction, environment, providers.getBeanProvider(AutomationAdmissionAuthority.class),
                providers.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class));
    }

    private static void rejectsLegacyStartForCompiledRelease(TestDatabaseVO db, ConfigurableApplicationContext reopened,
                                                              String releaseVersion) {
        var execution = new ProcessExecutionService(reopened.getBean(SpringProcessEngineConfiguration.class),
                reopened.getBean(SpringDmnEngineConfiguration.class), new ProcessModelValidator());
        var command = new ProcessExecutionService.StartCommand(releaseVersion, "review", "legacy-business",
                "legacy-automation-start", "legacy-correlation", Map.of(ProcessReleaseService.AUTOMATION_BINDING_REF, "test-only"));
        int before = count(db, "tianji_process_command_receipt");
        assertThrows(ProcessResourceNotFoundException.class, () -> execution.start(OWNER, command));
        assertEquals(before, count(db, "tianji_process_command_receipt"));
        assertEquals(0, count(db, "act_ru_execution"));
        assertEquals(0, count(db, "act_hi_procinst"));
    }

    private static void publishedDecisionRetainsItsOwnExactDeployment(TestDatabaseVO db,
                                                                        ConfigurableApplicationContext reopened,
                                                                        TestServicesVO components,
                                                                        TestServicesVO again) throws IOException {
        String rootXml = XML.replace("<endEvent id=\"end\"/>", """
                <businessRuleTask id="decide"/>
                <endEvent id="end"/>
                <sequenceFlow id="resolved" sourceRef="decide" targetRef="end"/>
                """).replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"decide\"");
        String decisionXml;
        try (var fixture = AutomationPublicationActivationIT.class.getResourceAsStream("/fixtures/baseline.dmn")) {
            assertNotNull(fixture);
            decisionXml = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
        }
        String decisionKey = "baselineDecision";
        byte[] decisionBytes = decisionXml.getBytes(StandardCharsets.UTF_8);
        var model = new AutomationModelSourceVO.ModelSourceVO("DMN", decisionKey, decisionXml);
        var input = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer")),
                "required", List.of("amount"), "additionalProperties", false));
        var output = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of("route", Map.of("type", "string")),
                "required", List.of("route"), "additionalProperties", false));
        var rootOutput = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(), "required", List.of(),
                "additionalProperties", false));
        var mapping = List.of(new AutomationNodeBindingVO.MappingVO("amount", "input.amount"));
        var ref = new AutomationNodeBindingVO.DecisionRefVO(decisionKey, AutomationModelValidator.rawSha256(decisionBytes),
                mapping, List.of(new AutomationNodeBindingVO.MappingVO("route", "scope.route")));
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var policy = new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(30), end, end, end,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        var binding = new AutomationNodeBindingVO("decide", "RULE", input, output, mapping, List.of(), policy,
                null, ref, null, null, null, null);
        var source = new AutomationModelSourceVO("AUTOMATION_V2",
                List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", "review", rootXml), model),
                input, rootOutput, List.of(binding), List.of());
        var content = AutomationReleaseStore.copyContent(source);
        var draft = components.drafts().create(OWNER, "review", new CreateProcessDraftCommand("Decision", null, "dmn-draft"));
        var saved = components.drafts().save(OWNER, draft.getDraftId(), new SaveProcessDraftCommand("dmn-ready", 1, "Ready", content));
        var validated = components.drafts().validate(OWNER, preview(saved));
        assertTrue(validated.isValid(), () -> validated.getIssues().toString());
        var command = command(saved, "dmn-release", "dmn-publish");
        var committed = publish(components.drafts(), OWNER, command);
        var compiled = new AutomationModelCompiler().compile(new AutomationModelValidator().validate(source, DependencySnapshotVO.empty(OWNER.tenantId())));
        assertEquals(compiled.dependencySha256(), committed.dependencyManifestSha256());
        assertArrayEquals(compiled.artifacts().get("review.bpmn20.xml").xml(), exactDeploymentBytes(db, committed.releaseVersion()));

        var repository = reopened.getBean(SpringDmnEngineConfiguration.class).getDmnRepositoryService();
        var nativeBinding = db.owner().queryForObject("""
                SELECT engine_definition_id,engine_deployment_id,engine_version,model_sha256
                FROM tianji_process.tianji_process_release_definition
                WHERE tenant_id=? AND release_key=? AND model_type='DMN' AND definition_key=?
                """, (row, index) -> new ProcessReleaseService.Definition(ValidatedProcessModel.Type.DMN, decisionKey,
                row.getString("engine_definition_id"), row.getString("engine_deployment_id"), row.getInt("engine_version"),
                row.getString("model_sha256"), Map.of()), OWNER.tenantId(), committed.releaseVersion(), decisionKey);
        assertNotNull(nativeBinding);
        assertEquals(AutomationModelValidator.rawSha256(decisionBytes), nativeBinding.modelSha256());
        var decision = repository.createDecisionQuery().decisionId(nativeBinding.engineDefinitionId())
                .deploymentId(nativeBinding.engineDeploymentId()).decisionVersion(nativeBinding.engineVersion())
                .decisionTenantId(Long.toString(OWNER.tenantId())).decisionKey(decisionKey).singleResult();
        assertNotNull(decision);
        assertEquals(decisionKey + ".dmn", decision.getResourceName());
        try (var persistedDmn = repository.getDmnResource(decision.getId())) {
            assertNotNull(persistedDmn);
            assertArrayEquals(decisionBytes, persistedDmn.readAllBytes());
        }
        assertEquals(source, again.drafts().getAutomationRelease(COPIER, committed.releaseVersion()).getArtifact());
        assertEquals(committed, publish(again.drafts(), OWNER, command));
        var copy = again.drafts().create(COPIER, "review",
                new CreateProcessDraftCommand("Decision copy", committed.releaseVersion(), "dmn-copy"));
        assertEquals(content, again.drafts().get(COPIER, copy.getDraftId(), null).getContent());
        rejectsDriftedPublicationProvenance(db, again.drafts(), source, command, committed);
        assertEquals(0, count(db, "tianji_process_automation_activation"));
        assertEquals(0, count(db, "act_ru_execution"));
    }

    private static void rejectsDriftedPublicationProvenance(TestDatabaseVO db, ProcessDraftService service,
                                                             AutomationModelSourceVO source,
                                                             PublishProcessDraftCommand originalCommand, DraftPublicationVO original) {
        int draftsBefore = count(db, "tianji_process_draft");
        int receiptsBefore = count(db, "tianji_process_draft_command");
        int deploymentsBefore = count(db, "act_re_deployment");
        assertEquals(1, changeSourceRevision(db, original, original.publishedRevision(), original.publishedRevision() + 1));
        try {
            assertAll(
                    () -> assertThrows(IllegalStateException.class, () -> service.getAutomationRelease(COPIER, original.releaseVersion())),
                    () -> assertThrows(IllegalStateException.class, () -> service.create(COPIER, "review",
                            new CreateProcessDraftCommand("Invalid provenance copy", original.releaseVersion(), "dmn-drift-copy"))),
                    () -> assertEquals(draftsBefore, count(db, "tianji_process_draft")),
                    () -> assertEquals(receiptsBefore, count(db, "tianji_process_draft_command")),
                    () -> assertEquals(deploymentsBefore, count(db, "act_re_deployment")));
        } finally {
            assertEquals(1, changeSourceRevision(db, original, original.publishedRevision() + 1, original.publishedRevision()));
        }
        assertEquals(original, publish(service, OWNER, originalCommand));
        assertEquals(source, service.getAutomationRelease(COPIER, original.releaseVersion()).getArtifact());
        assertEquals(deploymentsBefore, count(db, "act_re_deployment"));
    }

    private static int changeSourceRevision(TestDatabaseVO db, DraftPublicationVO original, long expected, long replacement) {
        return db.owner().update("""
                UPDATE tianji_process.tianji_process_automation_release SET source_draft_revision=?
                WHERE tenant_id=? AND release_key=? AND source_draft_id=? AND source_draft_revision=?
                """, replacement, OWNER.tenantId(), original.releaseVersion(), original.draftId(), expected);
    }

    private static void verifiedWorkerDependencyControlsFirstPublication(TestDatabaseVO db, ConfigurableApplicationContext first,
                                                                          ConfigurableApplicationContext reopened,
                                                                          TestServicesVO components, TestServicesVO again,
                                                                          MockEnvironment environment) {
        var schema = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(), "required", List.of(),
                "additionalProperties", false));
        String schemaSha256 = AutomationModelValidator.schemaSha256(schema);
        var capability = new AutomationNodeBindingVO.CapabilityRefVO("_customer.check", 7, schemaSha256, schemaSha256, WorkerEffectSafetyEnum.READ_ONLY);
        var end = new NodePolicyVO.PolicyActionVO("END", null);
        var policy = new NodePolicyVO(Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(30), end, end, end,
                new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()), "BEST_EFFORT_STOP", "WAIT_RECOVERY");
        var binding = new AutomationNodeBindingVO("work", "SERVICE", schema, schema, List.of(), List.of(), policy,
                capability, null, null, null, null, null);
        String sourceXml = XML.replace("<endEvent id=\"end\"/>", """
                <serviceTask id="work"/>
                <endEvent id="end"/>
                <sequenceFlow id="worked" sourceRef="work" targetRef="end"/>
                """).replace("sourceRef=\"start\" targetRef=\"end\"", "sourceRef=\"start\" targetRef=\"work\"");
        var models = List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", "review", sourceXml));
        var source = new AutomationModelSourceVO("AUTOMATION_V2", models, schema, schema, List.of(binding), List.of());
        var snapshot = new DependencySnapshotVO(OWNER.tenantId(), Map.of(new VersionedRefVO("_customer.check", 7),
                new WorkerRegistrationVO(capability, "a".repeat(64))), Map.of(), Set.of(), Map.of(), Map.of());
        var active = new AtomicBoolean(false);
        var observations = new AtomicInteger();
        var rechecks = new AtomicInteger();
        var authority = new AutomationReleaseStore.DependencyResolver() {
            @Override
            public DependencySnapshotVO resolve(ProcessActor requester, AutomationModelSourceVO model) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(OWNER, requester);
                assertEquals(source, model);
                observations.incrementAndGet();
                return snapshot;
            }

            @Override
            public boolean isCurrent(ProcessActor requester, AutomationModelSourceVO model, DependencySnapshotVO proof) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(OWNER, requester);
                assertEquals(source, model);
                assertSame(snapshot, proof);
                rechecks.incrementAndGet();
                return active.get();
            }
        };
        var trusted = service(first, components.releases(), environment, new AtomicBoolean(), authority);
        var trustedAfterRestart = service(reopened, again.releases(), environment, new AtomicBoolean(), authority);
        var draft = trusted.create(OWNER, "review", new CreateProcessDraftCommand("Check customer", null, "worker-draft"));
        var saved = trusted.save(OWNER, draft.getDraftId(),
                new SaveProcessDraftCommand("worker-ready", 1, "Ready", AutomationReleaseStore.copyContent(source)));
        var release = command(saved, "worker-release", "worker-publish");
        assertThrows(IllegalStateException.class, () -> again.drafts().validate(OWNER, preview(saved)));
        var rejected = assertThrows(IllegalStateException.class, () -> publish(trusted, OWNER, release));
        assertEquals("Automation publication dependency proof is no longer current", rejected.getMessage());
        assertEquals(4, count(db, "act_re_deployment"));
        assertNull(trusted.get(OWNER, draft.getDraftId(), release.getCommandId()).getCommandReceipt());

        active.set(true);
        var committed = publish(trusted, OWNER, release);
        var compiled = new AutomationModelCompiler().compile(new AutomationModelValidator().validate(source, snapshot));
        assertEquals(compiled.dependencySha256(), committed.dependencyManifestSha256());
        byte[] firstBytes = exactDeploymentBytes(db, committed.releaseVersion());
        assertArrayEquals(compiled.artifacts().get("review.bpmn20.xml").xml(), firstBytes);
        assertEquals(5, count(db, "act_re_deployment"));

        active.set(false);
        assertEquals(committed, publish(trustedAfterRestart, OWNER, release));
        assertArrayEquals(firstBytes, exactDeploymentBytes(db, committed.releaseVersion()));
        assertEquals(2, observations.get());
        assertEquals(2, rechecks.get());
        assertEquals(5, count(db, "act_re_deployment"));
        assertEquals(0, count(db, "tianji_process_automation_activation"));
    }

    private static byte[] exactDeploymentBytes(TestDatabaseVO db, String releaseVersion) {
        return db.owner().queryForObject("""
                SELECT data.bytes_ FROM tianji_process.act_ge_bytearray data
                JOIN tianji_process.tianji_process_release_definition native_binding
                  ON native_binding.engine_deployment_id=data.deployment_id_
                WHERE native_binding.tenant_id=? AND native_binding.release_key=?
                  AND native_binding.model_type='BPMN' AND data.name_='review.bpmn20.xml'
                """, byte[].class, OWNER.tenantId(), releaseVersion);
    }

    private static DraftPublicationVO publish(ProcessDraftService service, ProcessActor actor, PublishProcessDraftCommand command) {
        return service.publish(actor, command, Instant.now().plusSeconds(300));
    }

    private static DraftPublicationVO race(ProcessDraftService service, PublishProcessDraftCommand command, CountDownLatch entered,
                                           CountDownLatch start) throws InterruptedException {
        entered.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            return publish(service, OWNER, command);
        } catch (AutomationOperationException losingRevision) {
            assertEquals(AutomationOperationException.Code.TIANJI_DRAFT_STATE_CONFLICT, losingRevision.code());
            return null;
        }
    }

    private static ValidateProcessDraftQuery preview(DraftMutationResultDTO saved) {
        return new ValidateProcessDraftQuery(saved.getDraftId(), saved.getRevision(), saved.getContentSha256());
    }

    private static PublishProcessDraftCommand command(DraftMutationResultDTO saved, String version, String commandId) {
        return new PublishProcessDraftCommand(saved.getDraftId(), saved.getRevision(), saved.getContentSha256(), version, commandId);
    }

    private static TestServicesVO components(ConfigurableApplicationContext application, MockEnvironment environment) {
        var process = application.getBean(SpringProcessEngineConfiguration.class);
        var decision = application.getBean(SpringDmnEngineConfiguration.class);
        process.setBeans(Map.of());
        decision.setBeans(Map.of());
        var releases = new ProcessReleaseService(process, decision, new ProcessModelValidator());
        return new TestServicesVO(releases, service(application, releases, environment, new AtomicBoolean()));
    }

    private static ProcessDraftService service(ConfigurableApplicationContext application, ProcessReleaseService releases,
                                               MockEnvironment environment, AtomicBoolean injection) {
        return service(application, releases, environment, injection,
                (actor, model) -> AutomationModelValidator.DependencySnapshotVO.empty(actor.tenantId()));
    }

    private static ProcessDraftService service(ConfigurableApplicationContext application, ProcessReleaseService releases,
                                               MockEnvironment environment, AtomicBoolean injection,
                                               AutomationReleaseStore.DependencyResolver dependencies) {
        var process = application.getBean(SpringProcessEngineConfiguration.class);
        var manager = (DataSourceTransactionManager) process.getTransactionManager();
        var source = manager.getDataSource();
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        var tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var receipts = new ProcessDraftCommandReceiptStore(jdbc, SCHEMA) {
            @Override
            public boolean insertOriginal(ProcessDraftCommandReceiptBO receipt) {
                if ("forced-failure".equals(receipt.getCommandId()) && injection.compareAndSet(true, false)) {
                    assertEquals("PUBLISHED", jdbc.queryForObject("SELECT state FROM " + SCHEMA
                            + ".tianji_process_draft WHERE tenant_id=20 AND draft_id=?", String.class, receipt.getDraftId()));
                    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA
                            + ".act_re_deployment WHERE name_='tianji:rollback-target'", Integer.class));
                    throw new IllegalStateException("Test receipt failure");
                }
                return super.insertOriginal(receipt);
            }
        };
        return new ProcessDraftService(new ProcessDraftStore(jdbc, SCHEMA), receipts, new ProcessDraftContentCodec(),
                Mappers.getMapper(ProcessDraftConverter.class), new ProcessApiSchemaVerifier(source, SCHEMA),
                tx, environment, releases, new AutomationReleaseStore(jdbc, SCHEMA),
                new AutomationModelValidator(), new AutomationModelCompiler(), dependencies);
    }

    private static int count(TestDatabaseVO db, String table) {
        return db.owner().queryForObject("SELECT count(*) FROM " + SCHEMA + "." + table, Integer.class);
    }

    private static TestDatabaseVO provision() {
        String url = System.getProperty("tianji.process.postgres.test.url", "");
        String role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !role.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Publication requires a dedicated loopback database and runtime role");
        }
        var ownerSource = new DriverManagerDataSource(url);
        var properties = new Properties();
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        ownerSource.setConnectionProperties(properties);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(10);
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT current_database() LIKE 'tianji_it_%' AND NOT pg_is_in_recovery()
                   AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'
                """, Boolean.class)));
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT rolcanlogin AND NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb
                    AND NOT rolreplication AND NOT rolbypassrls
                    AND NOT has_database_privilege(rolname,current_database(),'CREATE')
                    AND NOT has_schema_privilege(rolname,'public','CREATE')
                FROM pg_roles WHERE rolname=?
                """, Boolean.class, role)));
        assertEquals(0, owner.queryForObject("""
                SELECT count(*) FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=?)
                """, Integer.class, role));

        owner.execute("CREATE SCHEMA " + SCHEMA);
        var flyway = Flyway.configure().dataSource(ownerSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").createSchemas(false).baselineOnMigrate(false)
                .locations("classpath:db/tianji-process").target("14").cleanDisabled(true).load();
        assertEquals(14, flyway.migrate().migrationsExecuted);
        flyway.validate();
        String scoped = url + "?currentSchema=" + SCHEMA;
        try (var installing = context(scoped, System.getProperty("user.name"), true)) {
            assertNotNull(installing.getBean(org.flowable.engine.RepositoryService.class));
        }
        String quoted = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quoted);
        grant(owner, quoted, "SELECT", "tianji_process_schema_history", "tianji_platform_release_activation");
        grant(owner, quoted, "SELECT,INSERT,UPDATE", "tianji_process_release", "tianji_process_release_definition",
                "tianji_process_command_receipt", "tianji_process_draft", "tianji_process_node_execution");
        grant(owner, quoted, "SELECT,INSERT", "tianji_governed_process_start", "tianji_process_automation_release",
                "tianji_process_draft_command", "tianji_process_automation_activation", "tianji_process_instance_binding");
        owner.execute("GRANT UPDATE (created_at) ON " + SCHEMA + ".tianji_process_instance_binding TO " + quoted);
        var nativeTables = owner.queryForList("""
                SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname=? AND tablename LIKE 'act#_%' ESCAPE '#'
                """, String.class, SCHEMA);
        assertTrue(nativeTables.contains("act_re_deployment"));
        for (String table : nativeTables) {
            assertTrue(table.matches("act_[a-z0-9_]+"));
            grant(owner, quoted, "SELECT,INSERT,UPDATE,DELETE", table);
        }
        assertFalse(Boolean.TRUE.equals(owner.queryForObject("SELECT has_schema_privilege(?, ?, 'CREATE')",
                Boolean.class, role, SCHEMA)));
        return new TestDatabaseVO(owner, scoped, role);
    }

    private static void grant(JdbcTemplate jdbc, String role, String privilege, String... names) {
        for (String name : names) jdbc.execute("GRANT " + privilege + " ON " + SCHEMA + "." + name + " TO " + role);
    }

    private static ConfigurableApplicationContext context(String url, String user, boolean install) {
        return new SpringApplicationBuilder(NativeTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user, "--spring.main.banner-mode=off",
                "--flowable.database-schema-update=" + install, "--egon.cola.component.transactional-outbox.enabled=false");
    }

    private record TestDatabaseVO(JdbcTemplate owner, String url, String role) { }
    private record TestServicesVO(ProcessReleaseService releases, ProcessDraftService drafts) { }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class NativeTestConfiguration { }
}
