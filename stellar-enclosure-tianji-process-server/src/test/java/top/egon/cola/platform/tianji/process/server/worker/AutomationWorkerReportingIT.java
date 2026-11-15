package top.egon.cola.platform.tianji.process.server.worker;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO.OutcomeEnum;
import top.egon.cola.platform.tianji.process.server.AutomationControlFlowIT.Harness;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.process.server.AutomationControlFlowIT.ISSUER;
import static top.egon.cola.platform.tianji.process.server.AutomationControlFlowIT.complete;

@Isolated("Owns one explicitly provisioned empty Process database")
@Timeout(120)
class AutomationWorkerReportingIT {

    private static final String NODE = "tianji_process.tianji_process_node_execution";
    private static final String LEASE = "tianji_process.tianji_worker_lease_receipt";
    private static final String COMMAND = "tianji_process.tianji_process_command_receipt";
    private static Harness fixture;

    @BeforeAll
    static void startFixture() throws Exception {
        fixture = Harness.open(false);
        fixture.publishService("typedReport", fixture.transactional, false);
        fixture.publishService("shortReport", fixture.shortLease, false);
        fixture.publishService("externalRetry", fixture.external, true);
        fixture.publishService("readOnlyRetry", fixture.readOnly, true);
    }

    @AfterAll
    static void stopFixture() {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void legacy_and_typed_native_topics_are_isolated_and_completed_report_replays_original_time() throws Exception {
        String legacyRoot = fixture.startLegacy();
        String root = fixture.start("typedReport", "report", 10);
        assertEquals("riskCheck:1", nativeTopic(legacyRoot));
        assertEquals(fixture.transactional.physicalTopic(), nativeTopic(root));
        assertNotEquals(nativeTopic(legacyRoot), nativeTopic(root));

        var registration = fixture.transactional;
        var acquire = new AutomationWorkerAcquireCommand(registration.capabilityKey(), registration.capabilityVersion());
        assertThrows(ProcessResourceNotFoundException.class, () -> fixture.worker.acquireAutomation(fixture.legacy.actor(), acquire));
        assertThrows(ProcessResourceNotFoundException.class,
                () -> fixture.worker.acquire(registration.actor(), new AcquireWorkerTaskCommand("riskCheck", "1")));
        var task = fixture.acquire(registration);
        assertEquals(task.getTaskId(), nativeId(root));
        assertEquals(nativeId(legacyRoot), fixture.acquireLegacy());
        assertEquals(Map.of("amount", 10L), task.getInput());
        task.verifyContractHash(registration.tenantId(), registration.sourceId());
        String canonicalInputSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ProcessTypedJson.canonicalBytes(task.getInput())));
        assertEquals(canonicalInputSha256, task.getInputSha256());
        var node = fixture.owner.queryForMap("SELECT input_sha256,configuration_sha256 FROM " + NODE + " WHERE instance_id=?", root);
        var lease = fixture.owner.queryForMap(
                "SELECT input_sha256,definition_id,element_id,occurrence_id,topic,schema_version FROM " + LEASE + " WHERE token_sha256=?",
                task.leaseTokenSha256());
        String nodeInput = (String) node.get("input_sha256");
        String leaseInput = (String) lease.get("input_sha256");
        assertNotEquals(canonicalInputSha256, nodeInput);
        assertNotEquals(canonicalInputSha256, leaseInput);
        assertNotEquals(nodeInput, leaseInput);
        assertEquals(WorkerLeaseStore.automationPhysicalInputSha256(task.getTaskId(), root, (String) lease.get("definition_id"),
                (String) lease.get("element_id"), task.getCapabilityKey(), task.getCapabilityVersion(), task.getNodeExecutionRef(),
                nodeInput, (String) node.get("configuration_sha256")), leaseInput);
        assertEquals(task.getActivityOccurrenceId(), lease.get("occurrence_id"));
        assertEquals(registration.capabilityKey(), lease.get("topic"));
        assertEquals(Long.toString(registration.capabilityVersion()), lease.get("schema_version"));

        var request = complete(task, "typed-report", "receipt-report", Map.of("approved", true));
        var report = completeUnderRootContention(root, registration.actor(), request);
        assertEquals(OutcomeEnum.COMPLETED, report.getOutcome());
        assertEquals("RESULT_ACCEPTED", fixture.nodeState(root, "work"));
        assertEquals("true", fixture.nodeValue(root, "work", "approved", true));
        assertNotNull(fixture.process.getRuntimeService().createProcessInstanceQuery().processInstanceId(root).singleResult());
        assertEquals(1, fixture.count(NODE, "WHERE tenant_id=? AND instance_id=?", registration.tenantId(), root));
        assertEquals(1, fixture.count(LEASE, "WHERE tenant_id=? AND scope_id=?", registration.tenantId(), root));
        var originalLease = fixture.owner.queryForMap("SELECT * FROM " + LEASE + " WHERE token_sha256=?", task.leaseTokenSha256());
        assertEquals("COMPLETED", originalLease.get("state"));
        assertEquals(report.getDecidedAt(), fixture.owner.queryForObject(
                "SELECT decided_at FROM " + LEASE + " WHERE token_sha256=?", OffsetDateTime.class, task.leaseTokenSha256()).toInstant());
        assertEquals(1, fixture.count(COMMAND, """
                WHERE tenant_id=? AND subject_type='SERVICE' AND command_type='WORKER_REPORT_V2' AND state='APPLIED'
                  AND command_id=? AND resource_id=?
                """, registration.tenantId(), request.getCommandId(), task.getNodeExecutionRef()));
        var management = fixture.process.getManagementService();
        assertEquals(0, management.createExternalWorkerJobQuery().processInstanceId(root).count());
        assertEquals(1, management.createJobQuery().processInstanceId(root).count());
        var wrongOutput = complete(task, request.getCommandId(), "receipt-report", Map.of("approved", false));
        var wrongToken = complete(task, request.getCommandId(), "receipt-report", Map.of("approved", true));
        byte[] token = new byte[32];
        token[0] = 87;
        wrongToken.setLeaseToken(Base64.getUrlEncoder().withoutPadding().encodeToString(token));
        var wrongOperation = new AutomationWorkerFailureCommand(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), request.getCommandId(), AutomationWorkerFailureCommand.KindEnum.UNKNOWN, "RESPONSE_LOST", null);
        assertThrows(ProcessConflictException.class, () -> fixture.worker.completeAutomation(registration.actor(), wrongOutput));
        assertThrows(ProcessConflictException.class, () -> fixture.worker.completeAutomation(registration.actor(), wrongToken));
        assertThrows(ProcessConflictException.class, () -> fixture.worker.failAutomation(registration.actor(), wrongOperation));
        var anotherIssuer = ProcessActor.verified(registration.tenantId(), ProcessActor.SubjectType.SERVICE,
                registration.subjectId(), "https://idp.test/other");
        assertThrows(ProcessResourceNotFoundException.class, () -> fixture.worker.completeAutomation(anotherIssuer, request));
        assertEquals("true", fixture.nodeValue(root, "work", "approved", true));
        assertEquals(report, fixture.worker.completeAutomation(registration.actor(), request));
        fixture.runJob(root);
        assertEquals("COMPLETED", fixture.nodeState(root, "work"));
        assertNotNull(fixture.process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(root).singleResult().getEndTime());

        assertEquals(report, fixture.worker.completeAutomation(registration.actor(), request));
        assertEquals(originalLease, fixture.owner.queryForMap("SELECT * FROM " + LEASE + " WHERE token_sha256=?", task.leaseTokenSha256()));
        assertEquals(1, fixture.count(COMMAND, "WHERE subject_type='SERVICE' AND command_id=?", request.getCommandId()));
        assertEquals(0, fixture.process.getManagementService().createJobQuery().processInstanceId(root).count());
    }

    private static AutomationWorkerReportDTO completeUnderRootContention(String root, ProcessActor actor,
            AutomationWorkerCompletionCommand request) throws Exception {
        var manager = (DataSourceTransactionManager) fixture.process.getTransactionManager();
        var probe = new JdbcTemplate(Objects.requireNonNull(manager.getDataSource()));
        probe.setQueryTimeout(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<AutomationWorkerReportDTO> complete = () -> {
            ready.countDown();
            assertTrue(start.await(3, TimeUnit.SECONDS));
            return fixture.worker.completeAutomation(actor, request);
        };
        try (var holder = Objects.requireNonNull(fixture.owner.getDataSource()).getConnection()) {
            holder.setAutoCommit(false);
            int holderPid;
            try (var lock = holder.prepareStatement("""
                    SELECT pg_backend_pid() FROM tianji_process.tianji_process_instance_binding
                    WHERE tenant_id=? AND instance_id=? FOR UPDATE
                    """)) {
                lock.setLong(1, actor.tenantId());
                lock.setString(2, root);
                try (var binding = lock.executeQuery()) {
                    assertTrue(binding.next());
                    holderPid = binding.getInt(1);
                    assertFalse(binding.next());
                }
            }
            try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
                var left = callers.submit(complete);
                var right = callers.submit(complete);
                try {
                    assertTrue(ready.await(2, TimeUnit.SECONDS));
                    start.countDown();
                    // The second backend can wait behind the first, so follow the blocker chain to the held root.
                    await().pollInterval(Duration.ofMillis(25)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                            assertEquals(2L, probe.queryForObject("""
                                    WITH RECURSIVE blocked(pid) AS (
                                        SELECT CAST(? AS integer)
                                        UNION
                                        SELECT waiter.pid
                                        FROM pg_catalog.pg_stat_activity waiter
                                        JOIN blocked blocker ON blocker.pid=ANY(pg_catalog.pg_blocking_pids(waiter.pid))
                                        WHERE waiter.datname=current_database() AND waiter.usename=current_user
                                          AND waiter.wait_event_type='Lock'
                                          AND waiter.query LIKE '%tianji_process_instance_binding%'
                                          AND waiter.query LIKE '%FOR UPDATE'
                                    )
                                    SELECT count(*) FROM blocked WHERE pid<>?
                                    """, Long.class, holderPid, holderPid)));
                    assertFalse(left.isDone());
                    assertFalse(right.isDone());
                } finally {
                    start.countDown();
                    holder.rollback();
                }
                var first = left.get(8, TimeUnit.SECONDS);
                var replay = right.get(8, TimeUnit.SECONDS);
                assertEquals(first, replay);
                assertEquals(first.getDecidedAt(), replay.getDecidedAt());
                return first;
            }
        }
    }

    @Test
    void original_short_lease_replays_after_its_database_deadline() {
        String root = fixture.start("shortReport", "expired-report", 12);
        var registration = fixture.shortLease;
        var task = fixture.acquire(registration);
        assertEquals(nativeId(root), task.getTaskId());
        var request = complete(task, "expired-report", "receipt-expired", Map.of("approved", true));
        var report = fixture.worker.completeAutomation(registration.actor(), request);
        var original = fixture.owner.queryForMap("SELECT * FROM " + LEASE + " WHERE token_sha256=?", task.leaseTokenSha256());
        fixture.runJob(root);
        await().atMost(Duration.ofSeconds(8)).pollInterval(Duration.ofMillis(100)).until(() -> Boolean.TRUE.equals(
                fixture.owner.queryForObject("SELECT lease_expires_at<=clock_timestamp() FROM " + LEASE
                        + " WHERE token_sha256=?", Boolean.class, task.leaseTokenSha256())));
        assertEquals(report, fixture.worker.completeAutomation(registration.actor(), request));
        assertEquals(original, fixture.owner.queryForMap("SELECT * FROM " + LEASE + " WHERE token_sha256=?", task.leaseTokenSha256()));
    }

    @Test
    void vanished_worker_after_write_lease_keeps_the_same_attempt_and_original_hard_deadline() {
        String root = fixture.start("externalRetry", "leased-write-timeout", 30);
        var task = fixture.acquire(fixture.external);
        assertEquals(nativeId(root), task.getTaskId());
        var hardDeadline = deadline(root);
        var hardTimer = fixture.boundaryTimer(root, true);
        expireAttempt(root);
        fixture.fireTimeout(root, false);

        assertEquals("WAIT_RECOVERY", fixture.nodeState(root, "work"));
        assertEquals("UNKNOWN", fixture.owner.queryForObject("SELECT side_effect_outcome FROM " + NODE + " WHERE instance_id=?",
                String.class, root));
        assertEquals(hardDeadline, deadline(root));
        var survivingTimer = fixture.boundaryTimer(root, true);
        assertEquals(hardTimer.getId(), survivingTimer.getId());
        assertEquals(hardTimer.getDuedate(), survivingTimer.getDuedate());
        assertEquals(1, fixture.count(NODE, "WHERE instance_id=?", root));
        assertEquals(1, fixture.count(LEASE, "WHERE scope_id=?", root));
        assertEquals(0, fixture.count(LEASE, "WHERE token_sha256=? AND result_sha256 IS NOT NULL", task.leaseTokenSha256()));
        assertEquals(0, fixture.count(COMMAND, "WHERE subject_type='SERVICE' AND (resource_id IN (?,?,?) OR release_key=?)",
                root, task.getTaskId(), task.getNodeExecutionRef(), "externalRetry-r1"));

        assertEquals(1, fixture.owner.update("UPDATE " + NODE
                + " SET node_deadline_at=clock_timestamp()-interval '1 second' WHERE instance_id=?", root));
        fixture.fireTimeout(root, true);
        assertEquals("TIMED_OUT", fixture.nodeState(root, "work"));
        assertNativeClean(root);
        assertEquals(1, fixture.count(NODE, "WHERE instance_id=?", root));
        assertNotNull(fixture.process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(root).singleResult().getEndTime());
    }

    @Test
    void retryable_write_report_without_effect_proof_parks_under_the_original_hard_timer() {
        String root = fixture.start("externalRetry", "leased-write-reported", 31);
        var task = fixture.acquire(fixture.external);
        assertEquals(nativeId(root), task.getTaskId());
        var hardTimer = fixture.boundaryTimer(root, true);
        var hardDeadline = deadline(root);
        var request = new AutomationWorkerFailureCommand(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), "retryable-write", AutomationWorkerFailureCommand.KindEnum.RETRYABLE, "TRANSIENT_WORK", null);
        var report = fixture.worker.failAutomation(fixture.external.actor(), request);
        assertEquals(OutcomeEnum.NEEDS_MANUAL, report.getOutcome());
        assertEquals("WAIT_RECOVERY", fixture.nodeState(root, "work"));
        assertEquals("UNKNOWN", fixture.owner.queryForObject("SELECT side_effect_outcome FROM " + NODE + " WHERE instance_id=?",
                String.class, root));
        var lease = fixture.owner.queryForMap("SELECT state,failure_kind,failure_code FROM " + LEASE + " WHERE token_sha256=?",
                task.leaseTokenSha256());
        assertEquals(Map.of("state", "NEEDS_MANUAL", "failure_kind", "RETRYABLE", "failure_code", "V2_RETRYABLE"), lease);
        assertEquals(1, fixture.count(COMMAND, "WHERE command_type='WORKER_REPORT_V2' AND command_id=? AND resource_id=?",
                request.getCommandId(), task.getNodeExecutionRef()));
        assertEquals(1, fixture.count(NODE, "WHERE instance_id=?", root));
        assertEquals(hardDeadline, deadline(root));
        assertEquals(hardTimer.getId(), fixture.boundaryTimer(root, true).getId());
        var management = fixture.process.getManagementService();
        assertEquals(0, management.createExternalWorkerJobQuery().processInstanceId(root).count());
        assertEquals(0, fixture.count("tianji_process.act_ru_timer_job",
                "WHERE process_instance_id_=? AND handler_type_='tianji-automation-worker-retry'", root));
        assertEquals(report, fixture.worker.failAutomation(fixture.external.actor(), request));
        var recovery = fixture.owner.queryForMap("""
                SELECT event_name_,execution_id_ FROM tianji_process.act_ru_event_subscr
                WHERE proc_inst_id_=? AND event_type_='message'
                """, root);
        assertThrows(RuntimeException.class, () -> fixture.process.getRuntimeService().messageEventReceived(
                (String) recovery.get("event_name_"), (String) recovery.get("execution_id_"), Map.of("resultRef", "forged")));
        assertEquals("WAIT_RECOVERY", fixture.nodeState(root, "work"));
        assertEquals(1, fixture.count("tianji_process.act_ru_event_subscr", "WHERE proc_inst_id_=?", root));
        fixture.cancel(root);
        assertNativeClean(root);
        assertEquals(report, fixture.worker.failAutomation(fixture.external.actor(), request));
    }

    @Test
    void unknown_report_keeps_the_full_error_code_and_the_fixed_v4_projection() {
        String root = fixture.start("externalRetry", "unknown-long-error", 32);
        var registration = fixture.external;
        var task = fixture.acquire(registration);
        assertEquals(nativeId(root), task.getTaskId());

        String prefix = "E" + "A".repeat(126);
        String errorCode = prefix + "Z";
        var request = new AutomationWorkerFailureCommand(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), "unknown-long-error", AutomationWorkerFailureCommand.KindEnum.UNKNOWN, errorCode, null);
        var report = fixture.worker.failAutomation(registration.actor(), request);
        assertEquals(OutcomeEnum.UNKNOWN, report.getOutcome());
        assertEquals("WAIT_RECOVERY", fixture.nodeState(root, "work"));

        String nodeSql = "SELECT error_code,side_effect_outcome FROM " + NODE
                + " WHERE tenant_id=? AND instance_id=? AND node_execution_ref=?";
        String leaseSql = "SELECT state,failure_kind,failure_code FROM " + LEASE + " WHERE tenant_id=? AND scope_id=? AND token_sha256=?";
        var node = Map.of("error_code", errorCode, "side_effect_outcome", "UNKNOWN");
        var lease = Map.of("state", "NEEDS_MANUAL", "failure_kind", "UNKNOWN", "failure_code", "V2_EFFECT_UNKNOWN");
        assertEquals(node, fixture.owner.queryForMap(nodeSql, registration.tenantId(), root, task.getNodeExecutionRef()));
        assertEquals(lease, fixture.owner.queryForMap(leaseSql, registration.tenantId(), root, task.leaseTokenSha256()));
        assertEquals(report, fixture.worker.failAutomation(registration.actor(), request));
        var differentLastCharacter = new AutomationWorkerFailureCommand(task.getTaskId(), task.getActivityOccurrenceId(),
                task.getNodeExecutionRef(), task.getLeaseToken(), request.getCommandId(),
                AutomationWorkerFailureCommand.KindEnum.UNKNOWN, prefix + "Y", null);
        assertThrows(ProcessConflictException.class, () -> fixture.worker.failAutomation(registration.actor(), differentLastCharacter));
        assertEquals(node, fixture.owner.queryForMap(nodeSql, registration.tenantId(), root, task.getNodeExecutionRef()));
        assertEquals(lease, fixture.owner.queryForMap(leaseSql, registration.tenantId(), root, task.leaseTokenSha256()));
        assertEquals(1, fixture.count(COMMAND, "WHERE tenant_id=? AND command_type='WORKER_REPORT_V2' AND command_id=? AND resource_id=?",
                registration.tenantId(), request.getCommandId(), task.getNodeExecutionRef()));
        fixture.cancel(root);
        assertNativeClean(root);
    }

    @Test
    void permanent_report_executes_the_native_failure_path_without_a_retry() {
        String root = fixture.start("typedReport", "permanent-report", 21);
        var registration = fixture.transactional;
        var task = fixture.acquire(registration);
        assertEquals(nativeId(root), task.getTaskId());
        var request = new AutomationWorkerFailureCommand(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), "permanent-report", AutomationWorkerFailureCommand.KindEnum.PERMANENT, "POLICY_REJECTED", null);
        var report = fixture.worker.failAutomation(registration.actor(), request);
        assertEquals(OutcomeEnum.PERMANENT, report.getOutcome());
        fixture.runJob(root);
        assertEquals("FAILED", fixture.nodeState(root, "work"));
        assertEquals(1, fixture.count(NODE, "WHERE instance_id=?", root));
        assertNativeClean(root);
        assertNotNull(fixture.process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(root).singleResult().getEndTime());
        assertEquals(report, fixture.worker.failAutomation(registration.actor(), request));
    }

    @Test
    void cancellation_stops_native_progress_and_keeps_an_already_decided_report() {
        String root = fixture.start("typedReport", "cancel-reported", 20);
        var registration = fixture.transactional;
        var task = fixture.acquire(registration);
        assertEquals(nativeId(root), task.getTaskId());
        var request = complete(task, "report-for-cancel", "receipt-for-cancel", Map.of("approved", true));
        var report = fixture.worker.completeAutomation(registration.actor(), request);
        fixture.cancel(root);
        assertEquals("CANCELLED", fixture.nodeState(root, "work"));
        assertEquals(report, fixture.worker.completeAutomation(registration.actor(), request));
        assertNativeClean(root);
        assertNotNull(fixture.process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(root)
                .singleResult().getDeleteReason());
    }

    @Test
    void safely_read_only_or_provably_unleased_attempt_can_retry_within_policy() {
        String read = fixture.start("readOnlyRetry", "read-only-timeout", 40);
        var leasedRead = fixture.acquire(fixture.readOnly);
        assertEquals(nativeId(read), leasedRead.getTaskId());
        assertSafeRetry(read);
        String unleased = fixture.start("externalRetry", "unleased-write-timeout", 50);
        assertEquals(0, fixture.count(LEASE, "WHERE scope_id=?", unleased));
        assertSafeRetry(unleased);
    }

    private void assertSafeRetry(String root) {
        var hardDeadline = deadline(root);
        expireAttempt(root);
        fixture.fireTimeout(root, false);
        assertEquals(1, fixture.count(NODE, "WHERE instance_id=? AND attempt=1 AND state='TIMED_OUT' AND side_effect_outcome='NONE'", root));
        assertEquals(0, fixture.process.getManagementService().createExternalWorkerJobQuery().processInstanceId(root).count());
        fixture.fireWorkerRetry(root);
        assertEquals(1, fixture.count(NODE, "WHERE instance_id=? AND attempt=2 AND node_deadline_at=?", root, hardDeadline));
        assertEquals(1, fixture.process.getManagementService().createExternalWorkerJobQuery().processInstanceId(root).count());
        fixture.cancel(root);
        assertNativeClean(root);
    }

    private static void assertNativeClean(String root) {
        var management = fixture.process.getManagementService();
        assertEquals(0, management.createJobQuery().processInstanceId(root).count());
        assertEquals(0, management.createExternalWorkerJobQuery().processInstanceId(root).count());
        assertEquals(0, management.createTimerJobQuery().processInstanceId(root).count());
        assertEquals(0, fixture.count("tianji_process.act_ru_event_subscr", "WHERE proc_inst_id_=?", root));
    }

    private static void expireAttempt(String root) {
        assertEquals(1, fixture.owner.update("UPDATE " + NODE + """
                 SET started_at=clock_timestamp()-interval '5 seconds',attempt_deadline_at=clock_timestamp()-interval '2 seconds'
                 WHERE instance_id=? AND attempt=1 AND accepted_at IS NULL
                """, root));
    }

    private static OffsetDateTime deadline(String root) {
        return fixture.owner.queryForObject("SELECT node_deadline_at FROM " + NODE
                + " WHERE instance_id=? ORDER BY attempt DESC LIMIT 1", OffsetDateTime.class, root);
    }

    private static String nativeTopic(String root) {
        return fixture.owner.queryForObject("SELECT handler_cfg_ FROM tianji_process.act_ru_external_job WHERE process_instance_id_=?",
                String.class, root);
    }

    private static String nativeId(String root) {
        return fixture.owner.queryForObject("SELECT id_ FROM tianji_process.act_ru_external_job WHERE process_instance_id_=?",
                String.class, root);
    }
}
