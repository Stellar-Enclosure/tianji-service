package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.GovernedStartAuthority;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.GovernedStartCommand;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.GovernedStartResult;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.StartAuthorizationQuery;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.StartCommand;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.StartGrant;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.WorkerRouteProof;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.WorkerRouteQuery;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ActivationGrant;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ActivationReceipt;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.AuthorizationRequest;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ChangeCommand;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.DeploymentProof;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.DeploymentQuery;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Authorization;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Grant;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Scope;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.SignedMaterial;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.TrustLease;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.TrustReference;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.ModelResource;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.SERVICE;
import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.USER;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.Action.ACTIVATE;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.Action.ROLLBACK;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.candidateSha256;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.canonical;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseArtifactValidator.hash;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Decision.ACCEPT;

/** Real Flowable and PostgreSQL with deliberately local, finite test authority. */
public final class PlatformActivationGovernedStartPostgresScenarios {
    private static final long TENANT = 20;
    private static final String CHANNEL = "expense";
    private static final String DEFINITION = "agentDispatch";
    private static final String TOPIC = "agentStart";
    private static final String SCHEMA_VERSION = "1";
    private static final String ISSUER = "https://process.example.test";
    private static final ProcessActor PUBLISHER = ProcessActor.verified(TENANT, SERVICE, "publisher", ISSUER);
    private static final ProcessActor SIGNER = ProcessActor.verified(TENANT, USER, "signer", ISSUER);
    private static final ProcessActor REVIEWER = ProcessActor.verified(TENANT, USER, "reviewer", ISSUER);
    private static final ProcessActor OPERATOR = ProcessActor.verified(TENANT, USER, "release-operator", ISSUER);
    private static final ProcessActor CALLER = ProcessActor.verified(TENANT, SERVICE, "caller", ISSUER);
    private static final String REGISTRATION = "2".repeat(64);
    private static final String AUDIT = "3".repeat(64);
    private static final String SCHEMA = "tianji_process.";
    private static final String ACTIVATION = SCHEMA + "tianji_platform_release_activation";
    private static final String PROVENANCE = SCHEMA + "tianji_governed_process_start";
    private static final String RECEIPT = SCHEMA + "tianji_process_command_receipt";
    private static final long TRIGGER_FENCE = 72360422038L;
    private static final String SHARED_START = "shared-start";
    private static final String FAIL_START = "forced-start-failure";
    private static final Map<String, ValueType> INPUTS = Map.of("amount", ValueType.INTEGER);

    private PlatformActivationGovernedStartPostgresScenarios() { }

    public static void verify(JdbcTemplate owner, SpringProcessEngineConfiguration engine,
                              SpringDmnEngineConfiguration dmn) throws Exception {
        var registration = new WorkerTopicRegistry.Registration(TENANT, ISSUER, "worker", TOPIC, SCHEMA_VERSION,
                Map.of("requestedAmount", ValueType.INTEGER), Map.of("runId", ValueType.STRING),
                Map.of("requestedAmount", Set.of("amount")), Map.of("runId", Set.of("agentRunId")), 1,
                Duration.ofSeconds(10), Duration.ofMillis(200), WorkerEffect.EXTERNAL_IDEMPOTENT, true);
        var binding = new AgentDispatchBinding(registration.actor(), "process-probe", TOPIC, SCHEMA_VERSION,
                "expenseAnalysis", 1, "a".repeat(64), "Inspect the expense.", Duration.ofMinutes(1));
        var validator = new ProcessModelValidator();
        var releases = new ProcessReleaseService(engine, dmn, validator, false,
                new WorkerTopicRegistry(List.of(registration)), true);
        var xml = new ClassPathResource("fixtures/agent-dispatch-wait.bpmn20.xml").getContentAsByteArray();
        var updatedXml = new String(xml, UTF_8).replace("<timeDuration>PT5M</timeDuration>",
                "<timeDuration>PT6M</timeDuration>").getBytes(UTF_8);
        assertNotEquals(hash(xml), hash(updatedXml));
        var original = releases.publish(PUBLISHER, "governed-v1", List.of(new ModelResource(
                Type.BPMN, DEFINITION, xml, INPUTS)));
        var updated = releases.publish(PUBLISHER, "governed-v2", List.of(new ModelResource(
                Type.BPMN, DEFINITION, updatedXml, INPUTS)));
        assertNotEquals(definition(original).engineDefinitionId(), definition(updated).engineDefinitionId());
        assertNotEquals(definition(original).engineDeploymentId(), definition(updated).engineDeploymentId());
        assertEquals(definition(original).engineVersion() + 1, definition(updated).engineVersion());

        Instant databaseTime = databaseNow(owner);
        var fixture = new Authorities(owner, binding, sign(databaseTime, original, updated));
        var reviews = new PlatformReleaseCandidateReviewService(engine, releases, fixture::trust, fixture::candidateGrant);
        var activation = new PlatformReleaseActivationService(engine, releases, fixture::trust, fixture::candidateGrant,
                fixture::activationGrant, fixture::deployment);
        var runs = new ProcessExecutionService(engine, dmn, validator, fixture);
        var originalScope = fixture.scope(original);
        var updatedScope = fixture.scope(updated);
        for (var scope : List.of(originalScope, updatedScope)) {
            assertEquals(scope, reviews.submit(PUBLISHER, scope, fixture.candidates.get(scope).material()).scope());
            assertEquals(ACCEPT, reviews.review(REVIEWER, scope,
                    new PlatformReleaseCandidateReviewService.ReviewCommand("accept", 0, ACCEPT)).command().decision());
        }

        var noPointer = start("before-activation");
        assertThrows(ProcessResourceNotFoundException.class, () -> runs.startGoverned(CALLER, noPointer));
        assertAbsent(owner, noPointer);
        var firstChange = new ChangeCommand(originalScope, "first-activation", 0, ACTIVATE);
        var first = activation.change(OPERATOR, firstChange);
        assertEquals(1, first.revision());
        assertNull(first.previousCandidateSha256());
        var firstStart = start("first-start");
        var firstResult = runs.startGoverned(CALLER, firstStart);
        assertPinned(owner, engine, firstResult, firstStart, original, originalScope, 1);

        try (ExecutorService workers = Executors.newFixedThreadPool(3)) {
            var stale = start("stale-start");
            var observed = new CountDownLatch(1);
            var resume = new CountDownLatch(1);
            fixture.beforeWorkerProof = query -> {
                assertEquals(1, query.revision());
                observed.countDown();
                await(resume);
            };
            var staleStart = workers.submit(() -> runs.startGoverned(CALLER, stale));
            try {
                await(observed);
                var ready = new CyclicBarrier(2);
                var a = new ChangeCommand(updatedScope, "second-activation-a", 1, ACTIVATE);
                var b = new ChangeCommand(updatedScope, "second-activation-b", 1, ACTIVATE);
                var competing = List.of(workers.submit(() -> activateTogether(activation, a, ready)),
                        workers.submit(() -> activateTogether(activation, b, ready)));
                var winners = List.of(competing.getFirst().get(8, TimeUnit.SECONDS),
                        competing.getLast().get(8, TimeUnit.SECONDS)).stream().flatMap(List::stream).toList();
                assertEquals(1, winners.size());
                assertEquals(2, winners.getFirst().revision());
                assertEquals(originalScope.candidateSha256(), winners.getFirst().previousCandidateSha256());
                assertEquals(winners.getFirst(), activation.change(OPERATOR, winners.getFirst().command()));
            } finally {
                fixture.beforeWorkerProof = query -> { };
                resume.countDown();
            }
            assertFutureFailure(staleStart, ProcessVersionConflictException.class);
            assertAbsent(owner, stale);

            fixture.workerRegistration = "7".repeat(64);
            var mismatched = start("worker-mismatch");
            assertThrows(AccessDeniedException.class, () -> runs.startGoverned(CALLER, mismatched));
            assertAbsent(owner, mismatched);
            fixture.workerRegistration = REGISTRATION;
            assertThrows(ProcessVersionConflictException.class, () -> activation.change(OPERATOR,
                    new ChangeCommand(updatedScope, "wrong-rollback-target", 2, ROLLBACK)));

            installStartProbe(owner);
            try {
                var secondStart = start(SHARED_START);
                var rollback = new ChangeCommand(originalScope, "rollback", 2, ROLLBACK);
                Future<GovernedStartResult> waitingStart;
                Future<ActivationReceipt> waitingRollback;
                try (Connection gate = owner.getDataSource().getConnection()) {
                    gate.setAutoCommit(false);
                    int gatePid = holdTriggerFence(gate);
                    waitingStart = workers.submit(() -> runs.startGoverned(CALLER, secondStart));
                    int startPid = awaitExclusiveWaiter(owner, gatePid, waitingStart);
                    assertAbsent(owner, secondStart);
                    waitingRollback = workers.submit(() -> activation.change(OPERATOR, rollback));
                    int rollbackPid = awaitExclusiveWaiter(owner, startPid, waitingRollback);
                    assertTenantFence(owner, startPid, rollbackPid);
                    assertEquals(2L, currentRevision(owner));
                    gate.commit();
                }
                var secondResult = waitingStart.get(8, TimeUnit.SECONDS);
                var third = waitingRollback.get(8, TimeUnit.SECONDS);
                assertEquals(3, third.revision());
                assertEquals(updatedScope.candidateSha256(), third.previousCandidateSha256());
                assertEquals(original.releaseKey(), third.processReleaseVersion());
                assertPinned(owner, engine, secondResult, secondStart, updated, updatedScope, 2);

                var afterRollback = start("after-rollback");
                var rolledBackResult = runs.startGoverned(CALLER, afterRollback);
                assertPinned(owner, engine, rolledBackResult, afterRollback, original, originalScope, 3);
                assertNotEquals(firstResult.instance().instanceId(), rolledBackResult.instance().instanceId());

                fixture.beforeWorkerProof = query -> { throw new AssertionError("replay reopened current Worker proof"); };
                assertEquals(firstResult, runs.startGoverned(CALLER, firstStart));
                assertEquals(secondResult, runs.startGoverned(CALLER, secondStart));
                assertEquals(first, activation.change(OPERATOR, firstChange));
                assertThrows(ProcessConflictException.class, () -> runs.startGoverned(CALLER, new GovernedStartCommand(
                        CHANNEL, DEFINITION, secondStart.businessKey(), secondStart.commandId(),
                        secondStart.correlationId(), Map.of("amount", 99L))));
                fixture.beforeWorkerProof = query -> { };

                var forced = start(FAIL_START);
                var failure = assertThrows(RuntimeException.class, () -> runs.startGoverned(CALLER, forced));
                assertTrue(hasMessage(failure, "forced provenance failure after native start and APPLIED receipt"));
                assertAbsent(owner, forced);
                assertEquals(3L, currentRevision(owner));
                assertEquals(3, owner.queryForObject("SELECT count(*) FROM " + PROVENANCE, Integer.class));
                assertPinned(owner, engine, firstResult, firstStart, original, originalScope, 1);
                assertPinned(owner, engine, secondResult, secondStart, updated, updatedScope, 2);
            } finally {
                owner.execute("DROP TRIGGER test_governed_start_probe ON " + PROVENANCE);
                owner.execute("DROP FUNCTION " + SCHEMA + "test_governed_start_probe()");
            }
        }

        var explicit = start("ordinary-start");
        var explicitCommand = new StartCommand(updated.releaseKey(), DEFINITION, explicit.businessKey(),
                explicit.commandId(), explicit.correlationId(), explicit.variables());
        var ordinary = runs.start(CALLER, explicitCommand);
        assertEquals(ordinary, runs.start(CALLER, explicitCommand));
        assertEquals(definition(updated).engineDefinitionId(), engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(ordinary.instanceId()).singleResult().getProcessDefinitionId());
        assertThrows(ProcessConflictException.class, () -> runs.startGoverned(CALLER, explicit));
        assertEquals(0, countForCommand(owner, PROVENANCE, explicit.commandId()));
        assertEquals(3L, currentRevision(owner));
    }

    private static List<ActivationReceipt> activateTogether(PlatformReleaseActivationService service, ChangeCommand command,
                                                            CyclicBarrier ready) throws Exception {
        ready.await(5, TimeUnit.SECONDS);
        try {
            return List.of(service.change(OPERATOR, command));
        } catch (ProcessVersionConflictException lost) {
            return List.of();
        }
    }

    private static void assertPinned(JdbcTemplate owner, SpringProcessEngineConfiguration engine, GovernedStartResult result,
                                     GovernedStartCommand command, Release release, Scope scope, long revision) {
        assertEquals(revision, result.activationRevision());
        assertEquals(scope.candidateSha256(), result.candidateSha256());
        assertEquals(release.releaseKey(), result.instance().releaseKey());
        String instanceId = result.instance().instanceId();
        var nativeInstance = engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult();
        assertNotNull(nativeInstance);
        assertEquals(definition(release).engineDefinitionId(), nativeInstance.getProcessDefinitionId());
        assertEquals(Long.toString(TENANT), nativeInstance.getTenantId());
        assertEquals(command.businessKey(), nativeInstance.getBusinessKey());
        assertEquals(instanceId, owner.queryForObject("""
                SELECT r.resource_id FROM tianji_process.tianji_process_command_receipt r
                JOIN tianji_process.tianji_governed_process_start p
                  USING (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)
                JOIN tianji_process.tianji_platform_release_activation a
                  ON a.tenant_id=p.tenant_id AND a.channel=p.channel AND a.revision=p.activation_revision
                WHERE r.tenant_id=? AND r.subject_type='SERVICE' AND r.subject_issuer=? AND r.subject_id=?
                  AND r.command_type='START' AND r.command_id=? AND r.state='APPLIED'
                  AND r.release_key=a.process_release_version AND r.request_sha256=p.resolved_request_sha256
                  AND r.resource_id=p.instance_id AND p.activation_revision=? AND a.candidate_sha256=?
                """, String.class, TENANT, CALLER.issuerFingerprint(), CALLER.subjectId(), command.commandId(),
                revision, scope.candidateSha256()));
    }

    private static void assertAbsent(JdbcTemplate owner, GovernedStartCommand command) {
        assertEquals(0, countForCommand(owner, PROVENANCE, command.commandId()));
        assertEquals(0, countForCommand(owner, RECEIPT, command.commandId()));
        for (String nativeTable : List.of("act_ru_execution", "act_hi_procinst")) {
            assertEquals(0, owner.queryForObject("SELECT count(*) FROM " + SCHEMA + nativeTable
                    + " WHERE business_key_=? AND tenant_id_=?", Integer.class, command.businessKey(), Long.toString(TENANT)));
        }
    }

    private static int countForCommand(JdbcTemplate owner, String table, String command) {
        return owner.queryForObject("SELECT count(*) FROM " + table + " WHERE tenant_id=? AND command_id=?",
                Integer.class, TENANT, command);
    }

    private static long currentRevision(JdbcTemplate owner) {
        return owner.queryForObject("SELECT max(revision) FROM " + ACTIVATION + " WHERE tenant_id=? AND channel=?",
                Long.class, TENANT, CHANNEL);
    }

    private static void installStartProbe(JdbcTemplate owner) {
        owner.execute("""
                CREATE FUNCTION tianji_process.test_governed_start_probe() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.command_id = 'shared-start' THEN
                    PERFORM pg_catalog.pg_advisory_xact_lock(72360422038);
                  ELSIF NEW.command_id = 'forced-start-failure' THEN
                    IF NOT EXISTS (SELECT 1 FROM tianji_process.act_ru_execution WHERE id_=NEW.instance_id)
                       OR NOT EXISTS (SELECT 1 FROM tianji_process.act_hi_procinst WHERE proc_inst_id_=NEW.instance_id)
                       OR NOT EXISTS (SELECT 1 FROM tianji_process.tianji_process_command_receipt r
                         WHERE r.tenant_id=NEW.tenant_id AND r.subject_type=NEW.subject_type
                           AND r.subject_issuer=NEW.subject_issuer AND r.subject_id=NEW.subject_id
                           AND r.command_type=NEW.command_type AND r.command_id=NEW.command_id
                           AND r.resource_id=NEW.instance_id AND r.state='APPLIED') THEN
                      RAISE EXCEPTION 'native start or APPLIED receipt missing inside provenance transaction';
                    END IF;
                    RAISE EXCEPTION 'forced provenance failure after native start and APPLIED receipt';
                  END IF;
                  RETURN NEW;
                END $$
                """);
        owner.execute("CREATE TRIGGER test_governed_start_probe BEFORE INSERT ON " + PROVENANCE
                + " FOR EACH ROW EXECUTE FUNCTION " + SCHEMA + "test_governed_start_probe()");
    }

    private static int holdTriggerFence(Connection gate) throws Exception {
        try (var statement = gate.createStatement()) {
            statement.execute("SELECT pg_catalog.pg_advisory_xact_lock(" + TRIGGER_FENCE + ")");
            try (var pid = statement.executeQuery("SELECT pg_catalog.pg_backend_pid()")) {
                assertTrue(pid.next());
                return pid.getInt(1);
            }
        }
    }

    private static int awaitExclusiveWaiter(JdbcTemplate owner, int blocker, Future<?> future) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        do {
            var waiters = owner.queryForList("""
                    SELECT pid FROM pg_catalog.pg_locks
                    WHERE locktype='advisory' AND NOT granted AND mode='ExclusiveLock'
                      AND ?=ANY(pg_catalog.pg_blocking_pids(pid))
                    """, Integer.class, blocker);
            if (waiters.size() == 1) return waiters.getFirst();
            if (future.isDone()) future.get();
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("expected one waiting PostgreSQL backend blocked by " + blocker);
    }

    private static void assertTenantFence(JdbcTemplate owner, int startPid, int rollbackPid) {
        assertEquals(1, owner.queryForObject("""
                SELECT count(*) FROM pg_catalog.pg_locks start_lock JOIN pg_catalog.pg_locks rollback_lock
                  USING (locktype, database, classid, objid, objsubid)
                WHERE start_lock.locktype='advisory' AND start_lock.pid=? AND start_lock.granted
                  AND start_lock.mode='ShareLock' AND rollback_lock.pid=? AND NOT rollback_lock.granted
                  AND rollback_lock.mode='ExclusiveLock'
                """, Integer.class, startPid, rollbackPid));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(8, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for fixture gate", interrupted);
        }
    }

    private static void assertFutureFailure(Future<?> future, Class<? extends Throwable> type) {
        var failure = assertThrows(ExecutionException.class, () -> future.get(8, TimeUnit.SECONDS));
        assertInstanceOf(type, failure.getCause());
    }

    private static boolean hasMessage(Throwable failure, String message) {
        for (var current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(message)) return true;
        }
        return false;
    }

    private static GovernedStartCommand start(String command) {
        return new GovernedStartCommand(CHANNEL, DEFINITION, "business-" + command, command,
                "correlation-" + command, Map.of("amount", 10L));
    }

    private static Definition definition(Release release) {
        assertEquals(1, release.definitions().size());
        return release.definitions().getFirst();
    }

    private static Instant databaseNow(JdbcTemplate owner) {
        return owner.queryForObject("SELECT pg_catalog.clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant());
    }

    private static Map<Scope, SignedCandidate> sign(Instant now, Release... releases) throws Exception {
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] spki = key.getPublic().getEncoded();
        String keyId = hash(spki);
        var scopeFields = Map.<String, Object>of("algorithm", "Ed25519", "channel", CHANNEL, "keyId", keyId,
                "purpose", "RELEASE_CANDIDATE_ARTIFACT", "role", "RELEASE_ARTIFACT_SIGNER", "schemaVersion", 1,
                "tenantId", Long.toString(TENANT));
        Instant issued = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).minusSeconds(60);
        Instant expires = issued.plusSeconds(600);
        var trust = new HashMap<>(scopeFields);
        trust.put("publicKey", Base64.getEncoder().encodeToString(spki));
        trust.put("notBefore", issued.minusSeconds(60).toString());
        trust.put("notAfter", expires.plusSeconds(60).toString());
        byte[] trustBytes = jsonLine(trust);
        String trustPin = hash(trustBytes);
        var signed = new HashMap<Scope, SignedCandidate>();
        var json = JsonMapper.builder().build();
        for (Release release : releases) {
            // Reuse the strict A2 shape. Release and model identity come from the native publisher.
            var jsonMap = new TypeReference<Map<String, Object>>() { };
            var template = json.readValue(PlatformReleaseSignatureVerifierTest.CANDIDATE, jsonMap);
            var candidate = new HashMap<>(json.convertValue(template.get("candidate"), jsonMap));
            var link = new HashMap<>(json.convertValue(candidate.get("agentLink"), jsonMap));
            link.put("processDefinitionKey", DEFINITION);
            link.put("workerElementId", "worker");
            candidate.put("agentLink", link);
            candidate.put("process", Map.of("manifestSha256", release.manifestSha256(), "releaseVersion", release.releaseKey(),
                    "state", "ARTIFACT_CHECKED", "models", List.of(Map.of("type", "BPMN", "definitionKey", DEFINITION,
                            "modelSha256", definition(release).modelSha256(), "inputs", Map.of("amount", "INTEGER")))));
            String candidatePin = candidateSha256(candidate);
            byte[] envelope = jsonLine(Map.of("candidate", candidate, "candidateSha256", candidatePin));
            var claims = new HashMap<>(scopeFields);
            claims.put("candidateSha256", candidatePin);
            claims.put("candidateEnvelopeSha256", hash(envelope));
            claims.put("issuedAt", issued.toString());
            claims.put("expiresAt", expires.toString());
            var signer = Signature.getInstance("Ed25519");
            signer.initSign(key.getPrivate());
            signer.update("TIANJI_PLATFORM_RELEASE_DETACHED_SIGNATURE_V1".getBytes(UTF_8));
            signer.update((byte) 0);
            signer.update(canonical(claims).getBytes(UTF_8));
            byte[] sidecar = jsonLine(Map.of("claims", claims,
                    "signature", Base64.getEncoder().encodeToString(signer.sign())));
            var proof = PlatformReleaseSignatureVerifier.verifyBytes(envelope, sidecar, trustBytes, candidatePin,
                    trustPin, TENANT, CHANNEL, Clock.fixed(now, ZoneOffset.UTC));
            signed.put(new Scope(TENANT, CHANNEL, candidatePin), new SignedCandidate(new SignedMaterial(envelope, sidecar),
                    trustBytes, new TrustReference(1, trustPin, keyId, SIGNER), proof));
        }
        return Map.copyOf(signed);
    }

    private static byte[] jsonLine(Map<String, ?> document) {
        return (canonical(document) + "\n").getBytes(UTF_8);
    }

    private record SignedCandidate(SignedMaterial material, byte[] trust, TrustReference reference,
                                   PlatformReleaseSignatureVerifier.VerifiedCandidate proof) { }

    private static final class Authorities implements GovernedStartAuthority {
        private final JdbcTemplate owner;
        private final AgentDispatchBinding binding;
        private final Map<Scope, SignedCandidate> candidates;
        private volatile Consumer<WorkerRouteQuery> beforeWorkerProof = query -> { };
        private volatile String workerRegistration = REGISTRATION;

        private Authorities(JdbcTemplate owner, AgentDispatchBinding binding, Map<Scope, SignedCandidate> candidates) {
            this.owner = owner;
            this.binding = binding;
            this.candidates = candidates;
        }

        private Scope scope(Release release) {
            return candidates.entrySet().stream().filter(entry -> release.releaseKey().equals(entry.getValue().proof().releaseVersion()))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
        }

        private TrustLease trust(Scope scope) {
            var signed = candidates.get(scope);
            if (signed == null) return null;
            Instant now = databaseNow(owner);
            return new TrustLease(scope, signed.reference(), signed.trust(), now, now.plusSeconds(20));
        }

        private Grant candidateGrant(Authorization query) {
            var candidate = candidates.get(query.scope());
            if (candidate == null) return null;
            boolean releaseMatches = candidate.proof().releaseVersion().equals(query.releaseVersion());
            boolean reader = List.of(PUBLISHER, REVIEWER, OPERATOR).contains(query.actor());
            boolean allowed = switch (query.action()) {
                case SUBMIT -> PUBLISHER.equals(query.actor());
                case REVIEW -> REVIEWER.equals(query.actor());
                case REVIEW_INDEPENDENT -> REVIEWER.equals(query.actor()) && releaseMatches
                        && SIGNER.equals(query.signer()) && PUBLISHER.equals(query.submitter());
                case READ_CANDIDATE -> reader;
                case READ_PROCESS_RELEASE -> reader && releaseMatches;
            };
            if (!allowed) return null;
            Instant now = databaseNow(owner);
            return new Grant(query, AUDIT, now, now.plusSeconds(20));
        }

        private ActivationGrant activationGrant(AuthorizationRequest request) {
            if (!OPERATOR.equals(request.actor()) || !candidates.containsKey(request.command().target())) return null;
            Instant now = databaseNow(owner);
            return new ActivationGrant(request, AUDIT, now, now.plusSeconds(20));
        }

        private DeploymentProof deployment(DeploymentQuery query) {
            var candidate = candidates.get(query.target());
            if (candidate == null || !candidate.proof().releaseVersion().equals(query.processReleaseVersion())) return null;
            Instant now = databaseNow(owner);
            return new DeploymentProof(query, candidate.proof().deployment(), binding, REGISTRATION, AUDIT,
                    now, now.plusSeconds(20));
        }

        @Override
        public StartGrant authorize(StartAuthorizationQuery query) {
            if (!CALLER.equals(query.actor()) || !CHANNEL.equals(query.channel()) || !DEFINITION.equals(query.definitionKey())) return null;
            Instant now = databaseNow(owner);
            return new StartGrant(query, AUDIT, now, now.plusSeconds(20));
        }

        @Override
        public WorkerRouteProof currentWorker(WorkerRouteQuery query) {
            if (!TOPIC.equals(query.topic()) || !SCHEMA_VERSION.equals(query.schemaVersion())) return null;
            beforeWorkerProof.accept(query);
            Instant now = databaseNow(owner);
            return new WorkerRouteProof(query, binding, workerRegistration, now, now.plusSeconds(20));
        }
    }
}
