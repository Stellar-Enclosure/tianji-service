package top.egon.cola.platform.tianji.process.server.worker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;
import top.egon.cola.platform.tianji.process.server.AutomationControlFlowIT.Harness;
import top.egon.cola.platform.tianji.process.server.api.WorkerController;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionConverter;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeRetryService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerDescriptor;
import top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerRegistration;
import top.egon.cola.platform.tianji.worker.JdbcWorkerExecutionStore;
import top.egon.cola.platform.tianji.worker.WorkerEffectEvidenceVO;
import top.egon.cola.platform.tianji.worker.WorkerEffectOutcomeResolver;
import top.egon.cola.platform.tianji.worker.WorkerEffectReconcileQuery;
import top.egon.cola.platform.tianji.worker.WorkerExecutionReceipt;
import top.egon.cola.platform.tianji.worker.WorkerHandlerFailureException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.process.server.AutomationControlFlowIT.ISSUER;
import static top.egon.cola.platform.tianji.process.server.AutomationControlFlowIT.complete;

@Isolated("Owns isolated Process and Worker recovery databases")
@Timeout(120)
class AutomationWorkerRecoveryIT {
    private static final String SCHEMA = "tianji_process";
    private static final String NODE = SCHEMA + ".tianji_process_node_execution";
    private static final String LEASE = SCHEMA + ".tianji_worker_lease_receipt";
    private static final String COMMAND = SCHEMA + ".tianji_process_command_receipt";
    private static final String BUSINESS = "automation_recovery_business";
    private static final String EFFECT = BUSINESS + ".tianji_worker_effect_receipt";
    private static final String TARGET = BUSINESS + ".target_dispatch";
    private static final String DEFINITION = "workerRecovery";
    private static final String SOURCE = "worker-source";
    private static final String AUTHORITY = "effect-authority";
    private static final String EVIDENCE = "a".repeat(64);
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "starter", ISSUER);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, Object> OUTPUT = Map.of("approved", true);
    private static final ProcessSchemaVO INPUT_SCHEMA = schema("amount", "integer");
    private static final ProcessSchemaVO OUTPUT_SCHEMA = schema("approved", "boolean");

    private static Harness fixture;
    private static BusinessFixtureVO business;
    private static ProcessNodeRetryService retry;
    private static ProcessNodeExecutionStore nodes;
    private static TransactionTemplate processTransaction;
    private static final AtomicReference<WorkerEffectRecoveryAuthority> authority = new AtomicReference<>();
    private static final AtomicInteger authorityCalls = new AtomicInteger();

    @BeforeAll
    static void provision() throws Exception {
        business = provisionBusiness();
        fixture = Harness.open(false);
        fixture.publishService(DEFINITION, fixture.external, true);
        var manager = (DataSourceTransactionManager) fixture.process.getTransactionManager();
        var jdbc = new JdbcTemplate(manager.getDataSource());
        var bindings = new ProcessInstanceBindingStore(jdbc, SCHEMA);
        nodes = new ProcessNodeExecutionStore(jdbc, SCHEMA);
        var guard = new AutomationInstanceGuardCommandInterceptor(jdbc, SCHEMA, bindings);
        processTransaction = new TransactionTemplate(manager);
        var beans = new StaticListableBeanFactory(Map.of("flowableWorkerService", fixture.worker,
                "workerEffectRecoveryAuthority", (WorkerEffectRecoveryAuthority) query -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    authorityCalls.incrementAndGet();
                    return authority.get().observe(query);
                }));
        retry = new ProcessNodeRetryService(bindings, nodes, guard, beans.getBeanProvider(FlowableWorkerService.class),
                beans.getBeanProvider(WorkerEffectRecoveryAuthority.class), processTransaction,
                new MockEnvironment().withProperty("stellar.tianji.process.worker-recovery.trusted-authorities", AUTHORITY),
                Mappers.getMapper(ProcessNodeExecutionConverter.class));
    }

    @AfterAll
    static void close() {
        if (fixture != null) fixture.close();
    }

    @Test
    void committed_external_effect_survives_worker_death_and_recovery_only_replays() throws Exception {
        String root = fixture.start(DEFINITION, "worker-crash", 10);
        var original = acquire(root);
        var hardDeadline = deadline(root, 1);
        var hardTimer = fixture.boundaryTimer(root, true).getId();
        try (var external = new TargetEndpoint(original.getEffectKey(), Set.of(), true)) {
            forceWorkerDeath(original, external);
            assertEquals(List.of(1L), generations(original));
            assertEquals(1, applied(original));
            assertEquals(List.of("STARTED", "1"), effect(original));
            expireAttempt(root);
            fixture.fireTimeout(root, false);
            assertParked(root, original, hardDeadline);
            assertEquals(hardTimer, fixture.boundaryTimer(root, true).getId());

            var request = retryRequest(root, original, "recover-crash");
            authority.set(query -> unavailableProof(query, WorkerEffectRecoveryProofVO.Status.PENDING));
            assertBlocked(root, request);
            authority.set(query -> new WorkerEffectRecoveryProofVO(WorkerEffectRecoveryProofVO.Status.APPLIED, query.sha256(),
                    AUTHORITY, query.getEffectKey(), "b".repeat(64), query.getTargetId(), 1L, now(), now().plusSeconds(45),
                    EVIDENCE, receipt(1), OUTPUT, AutomationWorkerBusinessResultDTO.resultSha256("b".repeat(64), receipt(1), OUTPUT)));
            assertBlocked(root, request);
            authority.set(query -> {
                Instant expired = fixture.owner.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
                return new WorkerEffectRecoveryProofVO(WorkerEffectRecoveryProofVO.Status.APPLIED, query.sha256(), AUTHORITY,
                        query.getEffectKey(), query.getEffectFingerprint(), query.getTargetId(), 1L, expired.minusSeconds(1),
                        expired, EVIDENCE, receipt(1), OUTPUT,
                        AutomationWorkerBusinessResultDTO.resultSha256(query.getEffectFingerprint(), receipt(1), OUTPUT));
            });
            assertBlocked(root, request);
            assertParked(root, original, hardDeadline);

            authority.set(query -> {
                assertRootAvailableToObserver(query);
                return observedProof(query, original, 1);
            });
            var scheduled = ask(root, request);
            assertFrozen(root, original, scheduled, WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED, hardDeadline);
            assertEquals(hardTimer, fixture.boundaryTimer(root, true).getId());
            int observations = authorityCalls.get();
            assertEquals(scheduled, ask(root, request));
            assertEquals(observations, authorityCalls.get());
            assertEquals(List.of("STARTED", "1"), effect(original), "Process recovery observation must not update the Worker ledger");

            var replacement = acquire(root);
            assertRecoveryTask(original, scheduled, replacement, WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED);
            var outcome = worker(external.localUri(), external::resolve, replacement);
            assertTrue(outcome.replayed());
            assertEquals(receipt(1), outcome.businessReceiptId());
            report(root, replacement, outcome, "report-recovered-crash");
            assertThrows(AccessDeniedException.class, () -> retry.retry(OWNER, root, request, () -> null));
            assertThrows(AccessDeniedException.class, () -> retry.retry(OWNER, root, request, () -> Instant.EPOCH));
            assertEquals(scheduled, ask(root, request));
            assertEquals(observations, authorityCalls.get());
            assertEquals(List.of("COMPLETED", "1"), effect(original));
            assertEquals(List.of(1L), generations(original));
            assertEquals(1, applied(original));
        }
    }

    @Test
    void final_absence_needs_frozen_authority_and_then_allows_exactly_one_next_generation() throws Exception {
        String root = fixture.start(DEFINITION, "effect-not-applied", 20);
        var original = acquire(root);
        var hardDeadline = deadline(root, 1);
        try (var external = new TargetEndpoint(original.getEffectKey(), Set.of(1L), false)) {
            assertUnknown(() -> worker(external.localUri(), external::resolve, original));
            assertEquals(List.of(1L), generations(original));
            assertEquals(0, applied(original));
            var ordinary = reapAndAcquire(root, original);
            assertNull(ordinary.getEffectRecovery());
            assertEquals(original.getTaskId(), ordinary.getTaskId());
            assertNotEquals(original.getLeaseToken(), ordinary.getLeaseToken());
            assertUnknown(() -> worker(external.localUri(), external::resolve, ordinary));
            assertEquals(List.of(1L), generations(original));
            assertEquals("1", effect(original).getLast());

            var failed = new AutomationWorkerFailureCommand(ordinary.getTaskId(), ordinary.getActivityOccurrenceId(),
                    ordinary.getNodeExecutionRef(), ordinary.getLeaseToken(), "not-applied-unknown",
                    AutomationWorkerFailureCommand.KindEnum.UNKNOWN, "RESPONSE_LOST", null);
            assertEquals(AutomationWorkerReportDTO.OutcomeEnum.UNKNOWN, fixture.worker.failAutomation(fixture.external.actor(), failed).getOutcome());
            assertEquals("WAIT_RECOVERY", fixture.nodeState(root, "work"));
            authority.set(query -> observedProof(query, original, 1));
            var request = retryRequest(root, ordinary, "freeze-next-generation");
            NodeRetryResultDTO scheduled;
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = pool.submit(() -> ask(root, request));
                var second = pool.submit(() -> ask(root, request));
                scheduled = first.get(15, TimeUnit.SECONDS);
                assertEquals(scheduled, second.get(15, TimeUnit.SECONDS));
            }
            assertFrozen(root, original, scheduled, WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED, hardDeadline);
            assertEquals(List.of(1L), generations(original), "Only the original generation is sent before the frozen Worker starts");
            var replacement = acquire(root);
            assertRecoveryTask(original, scheduled, replacement, WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED);
            var outcome = worker(external.localUri(), external::resolve, replacement);
            assertFalse(outcome.replayed());
            assertEquals(receipt(2), outcome.businessReceiptId());
            var reclaimed = reapAndAcquire(root, replacement);
            assertEquals(replacement.getEffectRecovery(), reclaimed.getEffectRecovery());
            assertEquals(replacement.getTaskId(), reclaimed.getTaskId());
            assertTrue(worker(external.localUri(), external::resolve, reclaimed).replayed());
            report(root, reclaimed, outcome, "report-recovered-next");
            assertEquals(List.of("COMPLETED", "2"), effect(original));
            assertEquals(List.of(1L, 2L), generations(original));
            assertEquals(1, applied(original));
        }
    }

    private static void assertUnknown(org.junit.jupiter.api.function.Executable use) {
        assertEquals(WorkerFailureKindEnum.UNKNOWN, assertThrows(WorkerHandlerFailureException.class, use).kind());
    }

    @Test
    void a_frozen_proof_that_expires_before_acquisition_never_issues_a_lease() throws Exception {
        String root = fixture.start(DEFINITION, "effect-proof-expiry", 30);
        var original = acquire(root);
        var hardDeadline = deadline(root, 1);
        var hardTimer = fixture.boundaryTimer(root, true).getId();
        try (var external = new TargetEndpoint(original.getEffectKey(), Set.of(1L), false)) {
            assertUnknown(() -> worker(external.localUri(), external::resolve, original));
            var failed = new AutomationWorkerFailureCommand(original.getTaskId(), original.getActivityOccurrenceId(),
                    original.getNodeExecutionRef(), original.getLeaseToken(), "proof-expiry-unknown",
                    AutomationWorkerFailureCommand.KindEnum.UNKNOWN, "RESPONSE_LOST", null);
            assertEquals(AutomationWorkerReportDTO.OutcomeEnum.UNKNOWN,
                    fixture.worker.failAutomation(fixture.external.actor(), failed).getOutcome());
            var request = retryRequest(root, original, "freeze-expiring-proof");
            var validUntil = new AtomicReference<Instant>();
            authority.set(query -> {
                var proof = observedProof(query, original, 1, Duration.ofSeconds(3));
                validUntil.set(proof.validUntil());
                return proof;
            });
            var scheduled = ask(root, request);
            assertFrozen(root, original, scheduled, WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED, hardDeadline);
            await().atMost(Duration.ofSeconds(8)).until(() -> !processNow().isBefore(validUntil.get()));
            int observations = authorityCalls.get();
            var acquired = fixture.worker.acquireAutomation(fixture.external.actor(), new AutomationWorkerAcquireCommand(
                    fixture.external.capabilityKey(), fixture.external.capabilityVersion()));
            assertEquals(AutomationWorkerAcquireDTO.StatusEnum.EMPTY, acquired.getStatus());
            assertNull(acquired.getTask());
            assertEquals("WAIT_RECOVERY", fixture.nodeState(root, "work"));
            assertEquals(1, fixture.count(NODE, "WHERE instance_id=? AND attempt=2 AND error_code='WORKER_RECOVERY_PROOF_EXPIRED'", root));
            assertEquals(1, fixture.count(LEASE, "WHERE scope_id=?", root));
            assertEquals(hardDeadline, deadline(root, 2));
            assertEquals(hardTimer, fixture.boundaryTimer(root, true).getId());
            assertEquals(List.of(1L), generations(original));
            assertEquals(0, applied(original));
            assertEquals(scheduled, ask(root, request));
            assertEquals(observations, authorityCalls.get());
            fixture.cancel(root);
        }
    }

    private static void assertRootAvailableToObserver(WorkerEffectRecoveryQuery query) {
        var observer = fixture.owner.getDataSource();
        assertNotNull(observer);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(observer));
        transaction.setTimeout(2);
        transaction.executeWithoutResult(status -> {
            assertEquals(query.getInstanceId(), fixture.owner.queryForObject("""
                    SELECT instance_id FROM tianji_process.tianji_process_instance_binding
                    WHERE tenant_id=? AND instance_id=? FOR UPDATE NOWAIT
                    """, String.class, query.getActor().tenantId(), query.getInstanceId()));
            status.setRollbackOnly();
        });
    }

    private static Instant processNow() {
        return fixture.owner.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
    }

    private static Instant now() {
        return business.owner().queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
    }

    private static ProcessSchemaVO schema(String name, String type) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(name, Map.of("type", type)),
                "required", List.of(name), "additionalProperties", false));
    }

    private static String receipt(long generation) {
        return "target-receipt-" + generation;
    }

    private static List<Long> generations(AutomationWorkerTaskDTO task) {
        return business.targetObserver().queryForList("SELECT dispatch_generation FROM " + TARGET + " WHERE effect_key=? ORDER BY dispatch_generation",
                Long.class, task.getEffectKey());
    }

    private static int applied(AutomationWorkerTaskDTO task) {
        return business.targetObserver().queryForObject("SELECT count(*) FROM " + TARGET + " WHERE effect_key=? AND applied",
                Integer.class, task.getEffectKey());
    }

    private static List<String> effect(AutomationWorkerTaskDTO task) {
        return business.owner().queryForObject("SELECT state,dispatch_generation FROM " + EFFECT + " WHERE effect_key=?",
                (rows, index) -> List.of(rows.getString("state"), rows.getString("dispatch_generation")), task.getEffectKey());
    }

    private static NodeRetryResultDTO ask(String root, NodeRetryCommand command) {
        return retry.retry(OWNER, root, command, () -> now().plusSeconds(30));
    }

    private static void assertBlocked(String root, NodeRetryCommand request) {
        assertEquals(AutomationOperationException.Code.TIANJI_NODE_RETRY_BLOCKED,
                assertThrows(AutomationOperationException.class, () -> ask(root, request)).code());
        assertEquals(1, fixture.count(NODE, "WHERE instance_id=?", root));
        assertEquals("WAIT_RECOVERY", fixture.nodeState(root, "work"));
    }

    private static NodeRetryCommand retryRequest(String root, AutomationWorkerTaskDTO held, String command) {
        var row = processTransaction.execute(status -> nodes.find(OWNER.tenantId(), root, held.getNodeExecutionRef()).orElseThrow());
        return new NodeRetryCommand(command, held.getNodeExecutionRef(), Mappers.getMapper(ProcessNodeExecutionConverter.class).versionOf(row),
                "Reconcile the authoritative business record", receipt(1));
    }

    private static WorkerEffectRecoveryProofVO unavailableProof(WorkerEffectRecoveryQuery query, WorkerEffectRecoveryProofVO.Status status) {
        return new WorkerEffectRecoveryProofVO(status, query.sha256(), null, query.getEffectKey(), query.getEffectFingerprint(),
                query.getTargetId(), null, null, null, null, null, null, null);
    }

    private static WorkerEffectRecoveryProofVO observedProof(WorkerEffectRecoveryQuery query, AutomationWorkerTaskDTO original, long generation) {
        return observedProof(query, original, generation, Duration.ofSeconds(45));
    }

    private static WorkerEffectRecoveryProofVO observedProof(WorkerEffectRecoveryQuery query, AutomationWorkerTaskDTO original,
                                                             long generation, Duration window) {
        assertEquals(OWNER, query.getActor());
        assertEquals(SOURCE, query.getSourceId());
        assertEquals(original.getTargetId(), query.getTargetId());
        assertEquals(original.getEffectKey(), query.getEffectKey());
        assertEquals(original.getEffectFingerprint(), query.getEffectFingerprint());
        assertEquals(original.getOutputSchemaSha256(), query.getOutputSchemaSha256());
        assertEquals(1, query.getAttempt());
        assertEquals(receipt(1), query.getBusinessReceiptRef());
        var proof = targetRecord(query.getEffectKey(), generation);
        Instant observed = now();
        Instant validUntil = observed.plus(window);
        assertTrue(validUntil.isBefore(query.getNodeDeadlineAt()));
        var status = proof.applied() ? WorkerEffectRecoveryProofVO.Status.APPLIED : WorkerEffectRecoveryProofVO.Status.NOT_APPLIED_FINAL;
        var output = proof.applied() ? OUTPUT : null;
        var result = proof.applied() ? AutomationWorkerBusinessResultDTO.resultSha256(query.getEffectFingerprint(), proof.receiptId(), output) : null;
        return new WorkerEffectRecoveryProofVO(status, query.sha256(), AUTHORITY, query.getEffectKey(), query.getEffectFingerprint(),
                query.getTargetId(), generation, observed, validUntil, EVIDENCE, proof.receiptId(), output, result);
    }

    private static AutomationWorkerTaskDTO acquire(String root) {
        var task = fixture.acquire(fixture.external);
        assertEquals(task.getTaskId(), fixture.owner.queryForObject("""
                SELECT id_ FROM tianji_process.act_ru_external_job WHERE process_instance_id_=?
                """, String.class, root));
        return task;
    }

    private static AutomationWorkerTaskDTO reapAndAcquire(String root, AutomationWorkerTaskDTO old) {
        // Make the old lease stale before claiming a fresh token.
        assertEquals(1, fixture.owner.update("UPDATE " + LEASE
                + " SET leased_at=clock_timestamp()-interval '4 minutes',lease_expires_at=clock_timestamp()-interval '2 minutes'"
                + " WHERE token_sha256=? AND state='LEASED'", old.leaseTokenSha256()));
        assertEquals(1, fixture.owner.update("""
                UPDATE tianji_process.act_ru_external_job SET lock_exp_time_=clock_timestamp()-interval '1 second' WHERE id_=?
                """, old.getTaskId()));
        fixture.owner.update("""
                UPDATE tianji_process.act_ru_execution SET lock_time_=clock_timestamp()-interval '1 second' WHERE id_=?
                """, root);
        return acquire(root);
    }

    private static OffsetDateTime deadline(String root, int attempt) {
        return fixture.owner.queryForObject("SELECT node_deadline_at FROM " + NODE + " WHERE instance_id=? AND attempt=?",
                OffsetDateTime.class, root, attempt);
    }

    private static void expireAttempt(String root) {
        assertEquals(1, fixture.owner.update("""
                UPDATE %s SET started_at=clock_timestamp()-interval '5 seconds',
                    attempt_deadline_at=clock_timestamp()-interval '2 seconds'
                WHERE instance_id=? AND attempt=1 AND state='WAITING'
                """.formatted(NODE), root));
    }

    private static void assertParked(String root, AutomationWorkerTaskDTO original, OffsetDateTime hardDeadline) {
        assertEquals("WAIT_RECOVERY", fixture.nodeState(root, "work"));
        assertEquals("UNKNOWN", fixture.owner.queryForObject("SELECT side_effect_outcome FROM " + NODE + " WHERE instance_id=?",
                String.class, root));
        assertEquals(1, fixture.count(NODE, "WHERE instance_id=?", root));
        assertEquals(hardDeadline, deadline(root, 1));
        assertEquals(1, fixture.count(LEASE, "WHERE token_sha256=?", original.leaseTokenSha256()));
        assertEquals(0, fixture.count(LEASE, "WHERE scope_id=? AND result_sha256 IS NOT NULL", root));
        assertEquals(0, fixture.count(COMMAND, "WHERE command_type='WORKER_REPORT_V2' AND resource_id=?", original.getNodeExecutionRef()));
        assertEquals(0, fixture.process.getManagementService().createExternalWorkerJobQuery().processInstanceId(root).count());
    }

    private static void assertFrozen(String root, AutomationWorkerTaskDTO original, NodeRetryResultDTO result,
                                     WorkerEffectRecoveryVO.Mode mode, OffsetDateTime hardDeadline) {
        assertEquals("SCHEDULED", result.getState());
        assertEquals(root, result.getInstanceId());
        assertEquals(original.getActivityOccurrenceId(), result.getOccurrenceId());
        assertEquals(2, result.getAttempt());
        assertNotEquals(original.getNodeExecutionRef(), result.getNodeExecutionRef());
        assertEquals(2, fixture.count(NODE, "WHERE instance_id=?", root));
        assertEquals(hardDeadline, deadline(root, 2));
        assertEquals(1, fixture.count(NODE, """
                WHERE instance_id=? AND attempt=2 AND retry_command_id=? AND node_execution_ref=?
                AND effect_key=? AND worker_effect_recovery->>'mode'=? AND worker_effect_recovery->>'observedGeneration'='1'
                """, root, result.getCommandId(), result.getNodeExecutionRef(), original.getEffectKey(), mode.name()));
    }

    private static void assertRecoveryTask(AutomationWorkerTaskDTO original, NodeRetryResultDTO scheduled, AutomationWorkerTaskDTO replacement,
                                           WorkerEffectRecoveryVO.Mode mode) {
        assertEquals(original.getActivityOccurrenceId(), replacement.getActivityOccurrenceId());
        assertEquals(original.getEffectKey(), replacement.getEffectKey());
        assertEquals(original.getEffectFingerprint(), replacement.getEffectFingerprint());
        assertEquals(scheduled.getNodeExecutionRef(), replacement.getNodeExecutionRef());
        assertNotEquals(original.getTaskId(), replacement.getTaskId());
        assertNotNull(replacement.getEffectRecovery());
        assertEquals(mode, replacement.getEffectRecovery().mode());
        assertEquals(1L, replacement.getEffectRecovery().observedGeneration());
        replacement.verifyContractHash(OWNER.tenantId(), SOURCE);
    }

    private static void report(String root, AutomationWorkerTaskDTO task, WorkerExecutionReceipt result, String commandId) {
        var request = complete(task, commandId, result.businessReceiptId(), result.typedOutput());
        var report = fixture.worker.completeAutomation(fixture.external.actor(), request);
        assertEquals(AutomationWorkerReportDTO.OutcomeEnum.COMPLETED, report.getOutcome());
        assertEquals("RESULT_ACCEPTED", fixture.nodeState(root, "work"));
        var hashes = fixture.owner.queryForObject("SELECT n.output_sha256,l.result_sha256 FROM " + NODE + " n JOIN " + LEASE
                        + " l ON n.native_job_id=l.job_id AND n.tenant_id=l.tenant_id WHERE n.node_execution_ref=? AND l.token_sha256=?",
                (rows, index) -> List.of(rows.getString(1), rows.getString(2)), task.getNodeExecutionRef(), task.leaseTokenSha256());
        assertEquals(3, Set.of(result.resultSha256(), hashes.getFirst(), hashes.getLast()).size());
        fixture.runJob(root);
        assertEquals("COMPLETED", fixture.nodeState(root, "work"));
        assertEquals(report, fixture.worker.completeAutomation(fixture.external.actor(), request));
    }

    private static WorkerExecutionReceipt worker(URI endpoint, WorkerEffectOutcomeResolver resolver, AutomationWorkerTaskDTO task) {
        return executeWorker(business.url(), business.role(), endpoint, task, resolver);
    }

    private static WorkerExecutionReceipt executeWorker(String url, String role, URI endpoint, AutomationWorkerTaskDTO task,
                                                         WorkerEffectOutcomeResolver resolver) {
        var runtime = new DriverManagerDataSource(url + "?currentSchema=" + BUSINESS);
        runtime.setUsername(role);
        var descriptor = new AutomationWorkerHandlerDescriptor(task.getCapabilityKey(), task.getCapabilityVersion(),
                task.getInputSchemaSha256(), task.getOutputSchemaSha256(), task.getEffectMode(), task.getHandlerVersion(), task.getTargetId());
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var handler = new AutomationWorkerHandlerRegistration(descriptor, INPUT_SCHEMA, OUTPUT_SCHEMA, null, (context, key) -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) throw new AssertionError("Target dispatch inside a Worker transaction");
            try {
                var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(25))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(
                                new TargetDispatchVO(key, context.dispatchGeneration())))).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) throw responseLost();
                return JSON.readValue(response.body(), AutomationWorkerBusinessResultDTO.class);
            } catch (IOException failed) {
                throw responseLost();
            } catch (InterruptedException failed) {
                Thread.currentThread().interrupt();
                throw responseLost();
            }
        });
        return new JdbcWorkerExecutionStore(runtime, new DataSourceTransactionManager(runtime), BUSINESS, SOURCE, 20,
                Duration.ofSeconds(12), Duration.ofSeconds(1), resolver).executeAutomation(task, handler);
    }

    private static WorkerHandlerFailureException responseLost() {
        return new WorkerHandlerFailureException(WorkerFailureKindEnum.UNKNOWN, "RESPONSE_LOST");
    }

    private static void forceWorkerDeath(AutomationWorkerTaskDTO original, TargetEndpoint target) throws Exception {
        Path childLog = Files.createTempFile("tianji-recovery-child-", ".log");
        Process child = null;
        try {
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            child = new ProcessBuilder(java, "-cp", classpath, WorkerCrashChild.class.getName())
                    .redirectErrorStream(true).redirectOutput(childLog.toFile()).start();
            try (var stdin = child.getOutputStream()) {
                stdin.write(JSON.writeValueAsBytes(new ChildInputVO(business.url(), business.role(), target.localUri().toString(), original)));
            }
            assertTrue(target.committed.await(12, TimeUnit.SECONDS), "Child did not reach the committed target boundary");
            assertTrue(child.isAlive());
            assertTrue(child.destroyForcibly().waitFor(8, TimeUnit.SECONDS));
            assertFalse(child.isAlive());
            assertNotEquals(0, child.exitValue());
            assertEquals(0, target.reported.get());
        } finally {
            if (child != null && child.isAlive()) child.destroyForcibly().waitFor(8, TimeUnit.SECONDS);
            Files.deleteIfExists(childLog);
        }
    }

    public static final class WorkerCrashChild {
        private WorkerCrashChild() { }

        public static void main(String[] args) throws Exception {
            var input = JSON.readValue(System.in, ChildInputVO.class);
            URI endpoint = URI.create(input.endpoint());
            var outcome = executeWorker(input.url(), input.role(), endpoint, input.task(), null);
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var request = HttpRequest.newBuilder(endpoint.resolve(WorkerController.COMPLETE_AUTOMATION_PATH)).timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(
                            complete(input.task(), "child-worker-complete", outcome.businessReceiptId(), outcome.typedOutput())))).build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        }
    }

    public record ChildInputVO(String url, String role, String endpoint, AutomationWorkerTaskDTO task) { }

    public record TargetDispatchVO(String effectKey, long generation) { }

    private record TargetRecordVO(boolean applied, String receiptId) { }

    private static TargetRecordVO targetRecord(String key, long generation) {
        return business.targetObserver().queryForObject("SELECT applied,receipt_id FROM " + TARGET + " WHERE effect_key=? AND dispatch_generation=?",
                (rows, index) -> new TargetRecordVO(rows.getBoolean(1), rows.getString(2)), key, generation);
    }

    private static final class TargetEndpoint implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicInteger reported = new AtomicInteger();
        private final CountDownLatch committed = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final String effectKey;
        private final Set<Long> rejected;
        private final boolean pause;

        private TargetEndpoint(String effectKey, Set<Long> rejected, boolean pause) throws IOException {
            this.effectKey = effectKey;
            this.rejected = rejected;
            this.pause = pause;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/dispatch", this::dispatch);
            server.createContext(WorkerController.COMPLETE_AUTOMATION_PATH, this::unexpectedReport);
            server.createContext(WorkerController.FAIL_AUTOMATION_PATH, this::unexpectedReport);
            server.setExecutor(executor);
            server.start();
        }

        private URI localUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/dispatch");
        }

        private void unexpectedReport(HttpExchange exchange) throws IOException {
            try (exchange) {
                reported.incrementAndGet();
                exchange.sendResponseHeaders(503, -1);
            }
        }

        private void dispatch(HttpExchange exchange) throws IOException {
            try (exchange) {
                var request = JSON.readValue(exchange.getRequestBody().readNBytes(512), TargetDispatchVO.class);
                if (!"POST".equals(exchange.getRequestMethod()) || !effectKey.equals(request.effectKey()) || request.generation() < 1) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                boolean applied = !rejected.contains(request.generation());
                String receiptId = applied ? receipt(request.generation()) : null;
                business.target().update("INSERT INTO " + TARGET + " VALUES(?,?,?,?)", effectKey, request.generation(), applied, receiptId);
                committed.countDown();
                if (pause) {
                    try {
                        if (!release.await(30, TimeUnit.SECONDS)) throw new IOException("Target response barrier timed out");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!applied) {
                    exchange.sendResponseHeaders(503, -1);
                    return;
                }
                byte[] answer = JSON.writeValueAsBytes(new AutomationWorkerBusinessResultDTO(receiptId, OUTPUT));
                exchange.sendResponseHeaders(200, answer.length);
                exchange.getResponseBody().write(answer);
            }
        }

        private WorkerEffectEvidenceVO resolve(WorkerEffectReconcileQuery query) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(effectKey, query.getEffectKey());
            var observation = targetRecord(effectKey, query.getDispatchGeneration());
            Instant observed = now();
            var status = observation.applied() ? WorkerEffectEvidenceVO.Status.APPLIED : WorkerEffectEvidenceVO.Status.NOT_APPLIED_FINAL;
            var result = observation.applied() ? AutomationWorkerBusinessResultDTO.resultSha256(query.getEffectFingerprint(),
                    observation.receiptId(), OUTPUT) : null;
            return new WorkerEffectEvidenceVO(status, effectKey, query.getEffectFingerprint(), query.getTargetId(),
                    query.getDispatchGeneration(), AUTHORITY, observed, observed.plusSeconds(30), EVIDENCE,
                    observation.receiptId(), observation.applied() ? OUTPUT : null, result);
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
            executor.close();
        }
    }

    private static BusinessFixtureVO provisionBusiness() {
        String url = System.getProperty("tianji.worker.nontransactional.postgres.test.url", "");
        String targetUrl = System.getProperty("tianji.worker.business.postgres.test.url", "");
        String role = System.getProperty("tianji.worker.business.postgres.test.runtime-user", "");
        String loopback = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}";
        String processUrl = System.getProperty("tianji.process.postgres.test.url");
        if (!url.matches(loopback) || !targetUrl.matches(loopback) || !role.matches("tianji_it_worker_[0-9a-f]{12}")
                || url.equals(processUrl) || targetUrl.equals(processUrl) || url.equals(targetUrl)) {
            throw new IllegalArgumentException("Recovery requires a separate loopback Worker database and dedicated restricted role");
        }
        var ownerSource = new DriverManagerDataSource(url);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(5);
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, BUSINESS));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT rolcanlogin AND NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb
                AND NOT rolreplication AND NOT rolbypassrls
                AND NOT has_database_privilege(rolname,current_database(),'CREATE')
                FROM pg_roles WHERE rolname=?
                """, Boolean.class, role)));
        owner.execute("CREATE SCHEMA " + BUSINESS);
        var flyway = Flyway.configure().dataSource(ownerSource).schemas(BUSINESS).defaultSchema(BUSINESS)
                .table("tianji_worker_schema_history").createSchemas(false).baselineOnMigrate(false)
                .locations("classpath:db/tianji-worker").target("3").cleanDisabled(true).load();
        assertEquals(3, flyway.migrate().migrationsExecuted);
        flyway.validate();
        owner.execute("GRANT USAGE ON SCHEMA " + BUSINESS + " TO " + role);
        owner.execute("GRANT SELECT ON " + BUSINESS + ".tianji_worker_schema_history TO " + role);
        owner.execute("GRANT SELECT,INSERT ON " + BUSINESS + ".tianji_worker_execution_receipt,"
                + BUSINESS + ".tianji_worker_nontransactional_intent TO " + role);
        owner.execute("GRANT SELECT,INSERT,UPDATE ON " + EFFECT + " TO " + role);
        var targetOwner = new JdbcTemplate(new DriverManagerDataSource(targetUrl));
        targetOwner.setQueryTimeout(5);
        assertEquals(0, targetOwner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, BUSINESS));
        assertEquals(Boolean.FALSE, targetOwner.queryForObject("SELECT has_database_privilege(?,current_database(),'CREATE')", Boolean.class, role));
        targetOwner.execute("CREATE SCHEMA " + BUSINESS);
        targetOwner.execute("CREATE TABLE " + TARGET + """
                (effect_key text NOT NULL, dispatch_generation bigint NOT NULL, applied boolean NOT NULL, receipt_id text,
                PRIMARY KEY(effect_key,dispatch_generation), CHECK (applied=(receipt_id IS NOT NULL)))
                """);
        targetOwner.execute("GRANT USAGE ON SCHEMA " + BUSINESS + " TO " + role);
        targetOwner.execute("GRANT INSERT ON " + TARGET + " TO " + role);
        var targetDataSource = new DriverManagerDataSource(targetUrl + "?currentSchema=" + BUSINESS);
        targetDataSource.setUsername(role);
        var target = new JdbcTemplate(targetDataSource);
        target.setQueryTimeout(5);
        return new BusinessFixtureVO(url, role, owner, targetOwner, target);
    }

    private record BusinessFixtureVO(String url, String role, JdbcTemplate owner, JdbcTemplate targetObserver, JdbcTemplate target) { }
}
