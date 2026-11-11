package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier.ApprovedObservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolReceiptCutForkHandoff.Cut;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Requires an explicit empty loopback database and pinned preinstalled vector; authority is controlled, not live IAM. */
@EnabledIfSystemProperty(named = "tianji.tool.journal.postgres.test.url", matches = ".+")
@Timeout(120)
class ToolExecutionJournalPostgresTest {
    private static final String RECEIPT_CUT_CHILD = "tianji.tool.journal.postgres.start-receipt-cut.child";
    private static final String RECEIPT_CUT_DIRECTORY = "tianji-tool-receipt-cut-";
    private static final String RECEIPT_CUT_MARKER = "committed";
    private static final int RECEIPT_CUT_MARKER_BYTES = 111;
    private static final long RECEIPT_CUT_SECONDS = 16;
    private static final URI RESOURCE = URI.create("https://agent.example/api");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER,
            "reviewer" + "界".repeat(248), "https://idp.example/20");
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor("process.start", 1, "start_process", "Bounded test operation",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference")),
            new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", "a".repeat(64), null));
    private static final ToolDescriptor READ_DESCRIPTOR = new ToolDescriptor("decision.evaluate", 1, "evaluate_decision", "Fixed recorder test",
            DESCRIPTOR.input(), DESCRIPTOR.output(), new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "decision.evaluate", DecisionClient.EXECUTE_RESOURCE_KEY, "f".repeat(64), null));
    private static final TrustedAgentActor EXECUTOR = TrustedAgentActor.verified(ACTOR.tenantId(), TrustedAgentActor.SubjectType.SERVICE,
            "decision-tool-service", "https://process-idp.example/20");

    @Test void opt_in_tool_journal_contract_and_atomicity() throws Exception {
        var disposable = disposableDatabase();
        var source = disposable.source();
        var jdbc = disposable.jdbc();
        var previous = flyway(source, "12"); assertEquals(12, previous.migrate().migrationsExecuted); previous.validate();
        var history = jdbc.queryForList("SELECT version,type,script,checksum FROM tianji_agent.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        assertThrows(AgentLedgerException.class, () -> new ToolExecutionStorageVerifier(source).verify());
        var current = flyway(source, "13"); assertEquals(1, current.migrate().migrationsExecuted); current.validate();
        assertEquals(history, jdbc.queryForList("SELECT version,type,script,checksum FROM tianji_agent.flyway_schema_history WHERE version IS NOT NULL AND version<>'13' ORDER BY installed_rank"));
        new ToolExecutionStorageVerifier(source).verify();
        catalogDrift(source, jdbc);
        var ledger = new AgentLedgerService(source); var capabilities = new ToolCapabilityStore(source, ledger); capabilities.verifyStorage();
        var intents = new ToolActionIntentStore(source, ledger, (request, boundary) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertEquals(ToolExecutionAuthorization.Boundary.PREPARE, boundary);
            return ToolExecutionAuthorization.Decision.ALLOW;
        });
        intents.verifyStorage(); var run = start(ledger, ACTOR); var binding = binding(run);
        capabilityEpochs(jdbc, capabilities, binding);
        var claim = ledger.claim(ACTOR, new GetAgentRunQuery(run.runId()), ledger.get(ACTOR, new GetAgentRunQuery(run.runId())).revision(), "tool-test", Duration.ofMinutes(5));
        var staleHint = new AgentRunLease(claim.lease().runId(), claim.lease().owner(), claim.lease().epoch(), now(jdbc).minusSeconds(60));
        assertEquals(LeaseHeartbeat.ACTIVE, ledger.heartbeat(ACTOR, staleHint, Duration.ofMinutes(5)));
        var action = new ToolActionRequest(ACTOR, DESCRIPTOR, "账".repeat(255), "版".repeat(128), Map.of("amount", 20L));
        Instant expiry = now(jdbc).plusSeconds(600).plusNanos(987);
        var prepared = intents.prepare(run, staleHint, action, DESCRIPTOR, expiry);
        assertEquals(expiry.truncatedTo(ChronoUnit.MICROS), prepared.expiresAt()); assertEquals("PREPARED", prepared.state());
        assertEquals(prepared, intents.prepare(run, staleHint, action, DESCRIPTOR, expiry.plusSeconds(60)));
        assertEquals(prepared, intents.current(run, staleHint, action, DESCRIPTOR).orElseThrow());
        assertEquals(1, count(jdbc, action));
        replayConflictAndScope(ledger, intents, run, staleHint, action, prepared);
        concurrentPreparation(jdbc, intents, run, staleHint);
        rollback(source, jdbc, intents, run, staleHint);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tianji_agent.agent_tool_action_intent SET state='EXPIRED',last_error_code=NULL,closed_at=clock_timestamp(),updated_at=clock_timestamp() WHERE tenant_id=? AND intent_id=?", ACTOR.tenantId(), UUID.fromString(action.intentId())));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tianji_agent.agent_tool_action_intent SET state='APPLIED' WHERE tenant_id=? AND intent_id=?", ACTOR.tenantId(), UUID.fromString(action.intentId())));
        jdbc.update("UPDATE tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=?", ACTOR.tenantId(), run.runId());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class, () -> intents.current(run, staleHint, action, DESCRIPTOR)).code());
        var queued = start(ledger, ACTOR); var queuedBinding = binding(queued); var reservation = capabilities.reserve(queuedBinding, DESCRIPTOR).orElseThrow();
        var beforeCancel = ledger.get(ACTOR, new GetAgentRunQuery(queued.runId()));
        ledger.cancel(ACTOR, new CancelAgentRunCommand(queued.runId(), "cancel-test", beforeCancel.revision(), "controlled cancellation"));
        assertFalse(capabilities.attach(evidence(reservation, now(jdbc).plusSeconds(30)), DESCRIPTOR)); assertTrue(capabilities.current(queuedBinding, DESCRIPTOR).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_invocation", Integer.class));
        invocationPreparation(source, jdbc, ledger, capabilities);
        readOnlyRecording(source, jdbc, ledger, capabilities);
        idempotentWriteJournal(source, jdbc, ledger, capabilities);
        fixedStartRecording(source, jdbc, ledger, capabilities);
        knownStartResultAccess(source, jdbc, ledger, capabilities);
        approvedWriteJournal(source, jdbc, ledger, capabilities);
        approvedStartRecording(source, jdbc, ledger, capabilities);
    }

    @Test
    @EnabledIfSystemProperty(named = "tianji.tool.journal.postgres.historical.start.receipt", matches = "true")
    void historical_start_receipts_append_positive_evidence_for_unknown_and_dispatched_actions() throws Exception {
        var disposable = disposableDatabase();
        var source = disposable.source();
        var jdbc = disposable.jdbc();
        var migrations = flyway(source, "13");
        assertEquals(13, migrations.migrate().migrationsExecuted);
        migrations.validate();
        var ledger = new AgentLedgerService(source);
        var capabilities = new ToolCapabilityStore(source, ledger);
        capabilities.verifyStorage();
        var fixture = new WriteFixture(source, jdbc, ledger, capabilities);

        for (int index = 0; index < 2; index++) {
            var request = fixture.request(index, fixture.action("historical-start-"));
            var action = request.action();
            var id = UUID.fromString(action.intentId());
            var run = request.binding().run();
            var prepared = fixture.store.prepareOrLoadIdempotentWrite(request).invocation();
            var attempt = fixture.begin(request, prepared);
            assertTrue(attempt.result().started());
            String priorState = index == 0 ? "UNKNOWN" : "DISPATCHING";
            if ("UNKNOWN".equals(priorState)) {
                var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", null);
                assertEquals("UNKNOWN", fixture.store.completeIdempotentWrite(request, attempt.result().attempt(),
                        attempt.nonce(), unknown).invocation().state());
            }
            assertEquals(priorState, invocationState(jdbc, request));
            assertEquals(priorState, writeActionFacts(jdbc, action).get("state"));

            var current = ledger.get(ACTOR, new GetAgentRunQuery(run.runId()));
            ledger.finish(ACTOR, request.lease(), current.revision(), new AgentExecutionResult(run.runId(), AgentRunStatusEnum.COMPLETED,
                    Map.of(), null, null, null, null, "UNKNOWN"));
            assertEquals("UNKNOWN", startBudgetState(jdbc, request));
            var process = new ControlledHistoricalReceipt(jdbc, request);
            var authorityChecks = new AtomicInteger();
            var allow = new AtomicBoolean(true);
            var service = new ToolStartReceiptReconciliationService(source, ledger, authorization -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(ACTOR, authorization.owner());
                assertEquals(run.runId(), authorization.dispatchRunId());
                assertEquals(id, authorization.intentId());
                assertEquals(EXECUTOR, authorization.executor());
                assertEquals(priorState, authorization.priorState());
                authorityChecks.incrementAndGet();
                return allow.get() ? Optional.of(new HistoricalToolReceiptAuthorization.Permit(UUID.randomUUID(),
                        authorization.bindingSha256(), 1, Instant.now(), authorization.deadlineAt())) : Optional.empty();
            }, process);

            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                    () -> service.observe(ACTOR, run.runId(), id)).code());
            assertEquals(0, authorityChecks.get());
            assertEquals(0, process.reads.get());
            // Only shorten the fixture's already-issued dispatch window. Keep its recorded dispatch and outcome intact.
            assertEquals(1, jdbc.update("""
                    UPDATE ONLY tianji_agent.agent_tool_invocation SET dispatch_not_after=dispatched_at+interval '1 microsecond'
                    WHERE tenant_id=? AND run_id=? AND invocation_id=? AND intent_id=? AND state=? AND dispatched_at IS NOT NULL
                      AND dispatched_at+interval '1 microsecond'<clock_timestamp()
                    """, ACTOR.tenantId(), run.runId(), request.invocationId(), id, priorState));
            var sourceBefore = historicalSourceRows(jdbc, request);
            var ownerMismatch = TrustedAgentActor.verified(ACTOR.tenantId(), ACTOR.subjectType(), ACTOR.subjectId(), "https://other-idp.example/20");
            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                    () -> service.observe(ownerMismatch, run.runId(), id)).code());
            assertEquals(0, authorityChecks.get());
            allow.set(false);
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                    () -> service.observe(ACTOR, run.runId(), id)).code());
            assertEquals(0, process.reads.get());
            allow.set(true);
            assertEquals(AgentErrorCodeEnum.TIANJI_SIDE_EFFECT_UNKNOWN, assertThrows(AgentLedgerException.class,
                    () -> service.observe(ACTOR, run.runId(), id)).code());
            assertEquals(1, process.reads.get());
            assertTrue(historicalReceipts(jdbc, id).isEmpty());
            assertEquals(sourceBefore, historicalSourceRows(jdbc, request));

            process.applied = true;
            var receipt = index == 0 ? service.observe(ACTOR, run.runId(), id)
                    : overlapHistoricalReceiptCommands(service, process, run.runId(), id);
            assertEquals(id, receipt.intentId());
            assertEquals(run.runId(), receipt.dispatchRunId());
            assertEquals("controlled-historical-instance", receipt.instanceId());
            assertEquals(process.processRecordedAt, receipt.recordedAt());
            var rows = historicalReceipts(jdbc, id);
            assertEquals(1, rows.size());
            var stored = rows.getFirst();
            assertEquals(run.runId(), stored.get("run_id"));
            assertEquals("APPLIED_OBSERVED", stored.get("status"));
            assertEquals(priorState, stored.get("prior_state"));
            assertEquals(process.target.query().requestSha256(), stored.get("process_hash"));
            assertEquals(receipt.observedAt(), ((Timestamp) stored.get("created_at")).toInstant());
            assertEquals(receipt, service.observe(ACTOR, run.runId(), id));
            assertEquals(index == 0 ? 6 : 8, authorityChecks.get());
            assertEquals(index == 0 ? 3 : 4, process.reads.get());
            process.instanceId = "conflicting-historical-instance";
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                    () -> service.observe(ACTOR, run.runId(), id)).code());
            assertEquals(rows, historicalReceipts(jdbc, id));
            assertEquals(sourceBefore, historicalSourceRows(jdbc, request));
        }
    }

    private static ToolStartReceiptReconciliationService.ReceiptVO overlapHistoricalReceiptCommands(
            ToolStartReceiptReconciliationService service, ControlledHistoricalReceipt process, String runId, UUID intent) throws Exception {
        process.overlap = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> service.observe(ACTOR, runId, intent));
            var second = pool.submit(() -> service.observe(ACTOR, runId, intent));
            var winner = first.get(12, TimeUnit.SECONDS);
            assertEquals(winner, second.get(12, TimeUnit.SECONDS));
            return winner;
        } finally {
            process.overlap = null;
        }
    }

    private static List<Map<String, Object>> historicalReceipts(JdbcTemplate jdbc, UUID intent) {
        return jdbc.queryForList("""
                SELECT r.run_id,r.created_at,r.request_hash,r.result::text AS exact_result,
                       r.result->>'status' AS status,r.result->'source'->>'priorState' AS prior_state,
                       r.result->'process'->>'requestSha256' AS process_hash
                FROM ONLY tianji_agent.agent_run_command_receipt r
                WHERE r.tenant_id=? AND r.subject_type=? AND r.subject_issuer=? AND r.subject_id=?
                  AND r.operation='TOOL_START_RECEIPT_RECONCILE' AND r.command_id=?
                """, ACTOR.tenantId(), ACTOR.subjectType().name(), ACTOR.issuerFingerprint(), ACTOR.subjectId(), intent + ":1");
    }

    private static Map<String, Object> historicalSourceRows(JdbcTemplate jdbc, ToolInvocationStore.PreparationRequestVO request) {
        return jdbc.queryForMap("""
                SELECT to_jsonb(a)::text AS action,to_jsonb(i)::text AS invocation,to_jsonb(d)::text AS run,
                       to_jsonb(b)::text AS budget,to_jsonb(c)::text AS capability
                FROM ONLY tianji_agent.agent_tool_action_intent a
                JOIN ONLY tianji_agent.agent_tool_invocation i
                  ON i.tenant_id=a.tenant_id AND i.run_id=a.dispatch_run_id AND i.invocation_id=a.dispatch_invocation_id
                JOIN ONLY tianji_agent.agent_run d ON d.tenant_id=i.tenant_id AND d.run_id=i.run_id
                JOIN ONLY tianji_agent.agent_budget_reservation b
                  ON b.tenant_id=i.tenant_id AND b.run_id=i.run_id AND b.reservation_id=i.reservation_id
                JOIN ONLY tianji_agent.agent_tool_capability c
                  ON c.tenant_id=i.tenant_id AND c.run_id=i.run_id AND c.capability_id=i.capability_id
                WHERE a.tenant_id=? AND a.intent_id=? AND i.run_id=? AND i.invocation_id=?
                """, ACTOR.tenantId(), UUID.fromString(request.action().intentId()), request.binding().run().runId(), request.invocationId());
    }

    private static final class ControlledHistoricalReceipt implements FixedBpmnStartReceiptRead {
        final ToolInvocationStore.PreparationRequestVO expected;
        final TargetVO target;
        final Instant processRecordedAt;
        final AtomicInteger reads = new AtomicInteger();
        boolean applied;
        CountDownLatch overlap;
        String instanceId = "controlled-historical-instance";

        ControlledHistoricalReceipt(JdbcTemplate jdbc, ToolInvocationStore.PreparationRequestVO expected) {
            this.expected = expected;
            var action = expected.action();
            var command = new StartProcessCommand("controlled-definition", "controlled-release",
                    action.businessKey(), action.intentId(), action.intentId(), action.arguments());
            target = new TargetVO("c".repeat(64), new GetProcessStartReceiptQuery(command.commandId(), OrchestrationRequestDigests.start(command)));
            processRecordedAt = now(jdbc);
        }

        @Override public TargetVO bind(Request request) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(expected.binding().run(), request.run());
            assertEquals(expected.action().requestSha256(), request.action().requestSha256());
            assertEquals(expected.descriptor(), request.descriptor());
            assertEquals(EXECUTOR, request.executor());
            return target;
        }

        @Override public Optional<ObservationVO> read(Request request, TargetVO expectedTarget, Instant deadlineAt) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(target, expectedTarget);
            assertEquals(EXECUTOR, request.executor());
            assertTrue(deadlineAt.isAfter(Instant.now()));
            reads.incrementAndGet();
            var rendezvous = overlap;
            if (rendezvous != null) {
                rendezvous.countDown();
                try {
                    assertTrue(rendezvous.await(4, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Controlled historical receipt rendezvous interrupted");
                }
            }
            return applied ? Optional.of(new ObservationVO(target, EXECUTOR,
                    new ProcessStartReceiptDTO(instanceId, ProcessStartReceiptDTO.StatusEnum.APPLIED, processRecordedAt),
                    "tianji:ps-start:v1:" + "d".repeat(64), deadlineAt)) : Optional.empty();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "tianji.tool.journal.postgres.start-receipt-cut", matches = "true")
    void preserves_a_committed_local_start_receipt_after_forced_exit_before_accounting() throws Exception {
        String url = System.getProperty("tianji.tool.journal.postgres.test.url");
        String databaseName = ToolReceiptCutForkHandoff.databaseName(url);
        var database = disposableDatabase();
        var jdbc = database.jdbc();
        String setupRole = jdbc.queryForObject("SELECT current_user", String.class);
        var request = new ToolReceiptCutForkHandoff(url, setupRole,
                System.getProperty("tianji.tool.journal.postgres.vector.version"));
        assertReceiptCutDatabase(jdbc, databaseName, request);
        var migrations = flyway(database.source(), "13");
        assertEquals(13, migrations.migrate().migrationsExecuted);
        migrations.validate();

        var ledger = new AgentLedgerService(database.source());
        var directory = Files.createTempDirectory(RECEIPT_CUT_DIRECTORY,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();
        var marker = directory.resolve(RECEIPT_CUT_MARKER);
        Cut cut;
        ReceiptProof recorded;
        AgentRunDTO active;
        try {
            var child = startReceiptCutChild(request, marker);
            try {
                assertNotEquals(ProcessHandle.current().pid(), child.pid());
                cut = awaitReceiptCut(child, marker);
                recorded = readCommittedReceipt(jdbc, cut);
                active = ledger.get(ACTOR, new GetAgentRunQuery(cut.runId()));
                assertEquals(AgentRunStatusEnum.RUNNING, active.status());
                assertEquals(1, active.attempt());
                assertEquals("RESERVED", recorded.budgetState());
                assertNotNull(recorded.leaseExpiresAt());
                assertTrue(ledger.getResult(ACTOR, new GetAgentRunQuery(cut.runId())).isEmpty());
                assertTrue(child.isAlive(), "original recorder must still be parked before accounting");
            } finally {
                reapReceiptCutChild(child);
            }
            assertNotEquals(0, child.exitValue());
        } finally {
            Files.deleteIfExists(marker);
            Files.deleteIfExists(directory);
        }

        assertEquals(recorded, readCommittedReceipt(jdbc, cut));
        var replacementSource = receiptCutDataSource(url, setupRole);
        var replacementJdbc = new JdbcTemplate(replacementSource);
        replacementJdbc.setQueryTimeout(5);
        assertReceiptCutDatabase(replacementJdbc, databaseName, request);
        var replacement = new AgentLedgerService(replacementSource);
        var query = new GetAgentRunQuery(cut.runId());
        var previousLease = new AgentRunLease(cut.runId(), recorded.leaseOwner(), recorded.leaseEpoch(), recorded.leaseExpiresAt());
        assertEquals(1, replacementJdbc.update("""
                UPDATE ONLY tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second'
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?
                  AND status='RUNNING' AND revision=? AND last_event_sequence=? AND lease_owner=? AND lease_epoch=?
                  AND lease_expires_at=? AND lease_expires_at>clock_timestamp() AND deadline_at>clock_timestamp()
                """, ACTOR.tenantId(), ACTOR.subjectType().name(), ACTOR.issuerFingerprint(), ACTOR.subjectId(), cut.runId(),
                active.revision(), active.lastEventSequence(), previousLease.owner(), previousLease.epoch(),
                Timestamp.from(previousLease.expiresAt())));
        assertEquals(1, replacement.reconcileDue(1));
        assertEquals(0, replacement.reconcileDue(1));
        var suspended = replacement.get(ACTOR, query);
        assertEquals(AgentRunStatusEnum.SUSPENDED, suspended.status());
        assertEquals("LEASE_EXPIRED", suspended.waitReason());
        assertEquals(active.attempt(), suspended.attempt());
        assertEquals(active.revision() + 1, suspended.revision());
        assertEquals(active.lastEventSequence() + 1, suspended.lastEventSequence());
        var events = replacement.events(ACTOR, new AgentEventsQuery(cut.runId(), active.lastEventSequence(), 2));
        assertEquals(1, events.size());
        assertEquals("RUN_STATUS_CHANGED", events.getFirst().type());
        assertEquals(Map.of("from", "RUNNING", "status", "SUSPENDED", "reason", "LEASE_EXPIRED"), events.getFirst().payload());
        assertEquals(LeaseHeartbeat.LEASE_LOST, replacement.heartbeat(ACTOR, previousLease, Duration.ofSeconds(5)));
        assertTrue(replacement.pendingRuns(1).isEmpty());
        assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> replacement.claim(ACTOR, query, suspended.revision(), "tool-cut-replacement", Duration.ofSeconds(5))).code());
        assertTrue(replacement.getResult(ACTOR, query).isEmpty());

        var retained = readCommittedReceipt(replacementJdbc, cut);
        assertEquals(recorded.facts(), retained.facts());
        assertEquals("UNKNOWN", retained.budgetState());
        assertEquals(recorded.leaseEpoch() + 1, retained.leaseEpoch());
        assertNull(retained.leaseOwner());
        assertNull(retained.leaseExpiresAt());
        var disposition = replacement.inspectRecovery(ACTOR, query);
        assertEquals(AgentLedgerService.AgentRecoveryLifecycleEnum.STALLED, disposition.lifecycle());
        assertEquals(AgentLedgerService.AgentRecoveryReasonEnum.LEASE_EXPIRED, disposition.reason());
        assertEquals(AgentLedgerService.AgentRecoveryEffectEnum.UNVERIFIED, disposition.agentEffect());
        assertEquals(new AgentLedgerService.AgentRecoveryToolEvidenceDTO(
                AgentLedgerService.AgentRecoveryToolCoverageEnum.COMPLETE, false, false, false, true), disposition.toolEvidence());
        assertEquals(suspended, replacement.get(ACTOR, query));
        assertEquals(retained, readCommittedReceipt(replacementJdbc, cut));
    }

    public static void main(String[] arguments) throws Exception {
        if (!Boolean.getBoolean(RECEIPT_CUT_CHILD) || arguments.length != 1) {
            throw new IllegalArgumentException("Disposable Tool receipt cut child is disabled");
        }
        var request = ToolReceiptCutForkHandoff.receive(System.in);
        var marker = Path.of(arguments[0]);
        var directory = marker.getParent();
        if (!marker.isAbsolute() || directory == null || !directory.equals(directory.toRealPath())
                || !directory.getFileName().toString().startsWith(RECEIPT_CUT_DIRECTORY)
                || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                        .equals(PosixFilePermissions.fromString("rwx------"))
                || !RECEIPT_CUT_MARKER.equals(marker.getFileName().toString())
                || Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Invalid disposable Tool receipt cut marker");
        }
        var source = receiptCutDataSource(request.url(), request.setupRole());
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        assertReceiptCutDatabase(jdbc, ToolReceiptCutForkHandoff.databaseName(request.url()), request);
        flyway(source, "13").validate();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run", Integer.class));
        var ledger = new AgentLedgerService(source);
        var capabilities = new ToolCapabilityStore(source, ledger);
        capabilities.verifyStorage();
        var fixture = new StartRecorderFixture(source, jdbc, ledger, capabilities);
        var cut = new Cut(fixture.run.runId(), fixture.request.invocationId(),
                UUID.fromString(fixture.request.action().intentId()));
        var calls = new AtomicInteger();
        var transport = controlledStartTransport(operation -> {
            assertEquals(1, calls.incrementAndGet());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("DISPATCHING", invocationState(jdbc, fixture.request));
            var action = writeActionFacts(jdbc, fixture.request.action());
            assertEquals("DISPATCHING", action.get("state"));
            assertEquals("UNKNOWN", action.get("side_effect_outcome"));
            assertNull(action.get("business_receipt_id"));
            assertEquals("RESERVED", startBudgetState(jdbc, fixture.request));
            return startObservation();
        }, false);
        fixture.recorder.executeIdempotentStart(transport, fixture.request, status -> {
            assertEquals(ToolExecutionOutcome.Status.SUCCEEDED, status);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, calls.get());
            assertEquals("RESERVED", readCommittedReceipt(jdbc, cut).budgetState());
            try {
                Files.createFile(marker, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.writeString(marker, cut.encode(), StandardCharsets.US_ASCII, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                new CountDownLatch(1).await(RECEIPT_CUT_SECONDS * 2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Tool receipt cut child was interrupted before force-stop", interrupted);
            } catch (IOException failure) {
                throw new AssertionError("Tool receipt cut child could not write its acknowledgement", failure);
            }
            fail("Tool receipt cut child exceeded its wait before force-stop");
        });
        fail("original Tool recorder returned before force-stop");
    }

    private static ReceiptProof readCommittedReceipt(JdbcTemplate jdbc, Cut cut) {
        return jdbc.queryForObject("""
                SELECT to_jsonb(i)::text AS invocation,to_jsonb(a)::text AS action,b.reservation_id,
                       b.lease_epoch AS budget_epoch,b.state AS budget_state,r.lease_epoch,r.lease_owner,r.lease_expires_at
                FROM ONLY tianji_agent.agent_tool_invocation i
                JOIN ONLY tianji_agent.agent_tool_action_intent a
                  ON (a.tenant_id,a.intent_id,a.dispatch_run_id,a.dispatch_invocation_id,a.dispatch_epoch,
                      a.dispatch_nonce_sha256,a.executor_issuer,a.executor_id)=
                     (i.tenant_id,i.intent_id,i.run_id,i.invocation_id,i.dispatch_epoch,
                      i.dispatch_nonce_sha256,i.executor_issuer,i.executor_id)
                JOIN ONLY tianji_agent.agent_run r ON (r.tenant_id,r.run_id)=(i.tenant_id,i.run_id)
                JOIN ONLY tianji_agent.agent_budget_reservation b
                  ON (b.tenant_id,b.run_id,b.reservation_id)=(i.tenant_id,i.run_id,i.reservation_id)
                WHERE i.tenant_id=? AND i.run_id=? AND i.invocation_id=? AND i.intent_id=?
                  AND (r.subject_type,r.subject_issuer,r.subject_id)=(?,?,?)
                  AND (i.owner_type,i.owner_issuer,i.owner_id)=(r.subject_type,r.subject_issuer,r.subject_id)
                  AND (a.owner_type,a.owner_issuer,a.owner_id)=(r.subject_type,r.subject_issuer,r.subject_id)
                  AND a.origin_run_id=r.run_id AND i.tool_key='process.recordedStart' AND i.tool_version=1
                  AND i.risk='IDEMPOTENT_WRITE' AND i.executor_issuer=? AND i.executor_id=?
                  AND i.dispatch_epoch=1 AND i.state='SUCCEEDED' AND a.state='APPLIED'
                  AND i.side_effect_outcome='CONFIRMED' AND a.side_effect_outcome='CONFIRMED'
                  AND i.business_receipt_id=? AND a.business_receipt_id=i.business_receipt_id
                  AND i.outcome=a.result AND i.outcome->'output'->>'instanceId'=?
                  AND i.finished_at IS NOT NULL AND a.closed_at IS NOT NULL AND b.kind='TOOL'
                  AND b.actual_cost IS NULL AND b.actual_input_tokens IS NULL AND b.actual_output_tokens IS NULL
                  AND ((b.state='RESERVED' AND b.settled_at IS NULL AND r.active_tool_calls=1)
                    OR (b.state='UNKNOWN' AND b.settled_at IS NOT NULL AND r.active_tool_calls=0))
                  AND r.result IS NULL AND r.finished_at IS NULL AND r.tool_calls_reserved=1 AND r.model_calls_reserved=0
                  AND NOT EXISTS (SELECT 1 FROM ONLY tianji_agent.agent_event e
                        WHERE (e.tenant_id,e.run_id)=(r.tenant_id,r.run_id)
                          AND e.event_type IN ('TOOL_FINISHED','AGENT_OUTPUT'))
                  AND (SELECT count(*) FROM ONLY tianji_agent.agent_tool_invocation x
                        WHERE (x.tenant_id,x.run_id)=(r.tenant_id,r.run_id))=1
                  AND (SELECT count(*) FROM ONLY tianji_agent.agent_budget_reservation x
                        WHERE (x.tenant_id,x.run_id)=(r.tenant_id,r.run_id))=1
                """, (row, index) -> {
                    var expiration = row.getTimestamp("lease_expires_at");
                    return new ReceiptProof(new ReceiptFacts(row.getString("invocation"), row.getString("action"),
                            row.getString("reservation_id"), row.getLong("budget_epoch")), row.getString("budget_state"),
                            row.getLong("lease_epoch"), row.getString("lease_owner"), expiration == null ? null : expiration.toInstant());
                }, ACTOR.tenantId(), cut.runId(), cut.invocationId(), cut.intentId(), ACTOR.subjectType().name(),
                ACTOR.issuerFingerprint(), ACTOR.subjectId(), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId(),
                startObservation().hostReceiptReference(), startObservation().instanceId());
    }

    private record ReceiptFacts(String invocation, String action, String reservationId, long budgetEpoch) {
        @Override public String toString() { return "ReceiptFacts[redacted]"; }
    }

    private record ReceiptProof(ReceiptFacts facts, String budgetState, long leaseEpoch, String leaseOwner,
                                Instant leaseExpiresAt) {
        @Override public String toString() { return "ReceiptProof[redacted]"; }
    }

    private static void assertReceiptCutDatabase(JdbcTemplate jdbc, String databaseName, ToolReceiptCutForkHandoff request) {
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_database()=? AND current_user=? AND inet_server_addr()=inet '127.0.0.1'
                  AND NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'
                  AND current_setting('session_replication_role')='origin' AND current_setting('transaction_read_only')='off'
                """, Boolean.class, databaseName, request.setupRole())));
        assertEquals(request.vectorVersion(), jdbc.queryForObject("""
                SELECT e.extversion FROM pg_catalog.pg_extension e
                JOIN pg_catalog.pg_namespace n ON n.oid=e.extnamespace
                WHERE e.extname='vector' AND n.nspname='public'
                """, String.class));
    }

    private static DriverManagerDataSource receiptCutDataSource(String url, String role) {
        var source = new DriverManagerDataSource(url);
        source.setUsername(role);
        var limits = new Properties();
        limits.setProperty("connectTimeout", "3");
        limits.setProperty("loginTimeout", "3");
        limits.setProperty("socketTimeout", "8");
        limits.setProperty("options", "-c statement_timeout=6000 -c lock_timeout=2000");
        source.setConnectionProperties(limits);
        return source;
    }

    private static Process startReceiptCutChild(ToolReceiptCutForkHandoff request, Path marker) throws IOException {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        if (classpath == null || classpath.isBlank()) throw new IllegalStateException("Tool receipt cut child classpath is missing");
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath();
        var builder = new ProcessBuilder(java.toString(), "-D" + RECEIPT_CUT_CHILD + "=true",
                "-Dotel.traces.exporter=none", "-Dotel.metrics.exporter=none", "-Dotel.logs.exporter=none",
                "-cp", classpath, ToolExecutionJournalPostgresTest.class.getName(), marker.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().put("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS", "false");
        var child = builder.start();
        try {
            try (var pipe = child.getOutputStream()) {
                request.send(pipe);
            }
            return child;
        } catch (IOException | RuntimeException failure) {
            try { reapReceiptCutChild(child); }
            catch (RuntimeException | AssertionError cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static Cut awaitReceiptCut(Process child, Path marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RECEIPT_CUT_SECONDS);
        while (System.nanoTime() - deadline < 0 && child.isAlive()) {
            if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(marker, LinkOption.NOFOLLOW_LINKS));
                try (var stream = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] bytes = stream.readNBytes(RECEIPT_CUT_MARKER_BYTES + 1);
                    assertTrue(bytes.length <= RECEIPT_CUT_MARKER_BYTES);
                    if (bytes.length == RECEIPT_CUT_MARKER_BYTES) {
                        return Cut.decode(new String(bytes, StandardCharsets.US_ASCII));
                    }
                }
            }
            Thread.sleep(20);
        }
        return fail("original recorder did not acknowledge the committed local Tool receipt");
    }

    private static void reapReceiptCutChild(Process child) {
        if (child.isAlive()) child.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RECEIPT_CUT_SECONDS);
        try {
            while (child.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) fail("Tool receipt cut child was not reaped");
                try {
                    assertTrue(child.waitFor(remaining, TimeUnit.NANOSECONDS), "Tool receipt cut child was not reaped");
                } catch (InterruptedException waitInterrupted) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static DisposableDatabase disposableDatabase() {
        String url = System.getProperty("tianji.tool.journal.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) throw new IllegalArgumentException("Tool journal tests require an explicit empty loopback tianji_it_ database");
        String vectorVersion = System.getProperty("tianji.tool.journal.postgres.vector.version");
        if (vectorVersion == null || !vectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Tool journal tests require an explicitly pinned preinstalled vector patch");
        var source = new DriverManagerDataSource(url); var properties = new Properties();
        properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "15"); source.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        assertEquals(vectorVersion, jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
        return new DisposableDatabase(source, jdbc);
    }

    private record DisposableDatabase(DriverManagerDataSource source, JdbcTemplate jdbc) { }

    /** Controlled ports prove Recorder composition over the real journal; no IAM or Process HTTP call is made. */
    private static void approvedStartRecording(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                               ToolCapabilityStore capabilities) {
        var fixture = new ApprovalFixture(source, jdbc, ledger, capabilities);
        var recorder = new ToolInvocationRecorder(source, ledger, fixture.store, capabilities, fixture.authority); recorder.verifyStorage();
        var approvals = new ControlledApprovalVerifier(fixture); var transport = new ControlledApprovedStartTransport(jdbc);
        var action = new ToolActionRequest(ACTOR, fixture.descriptor, "approved-recorder-" + UUID.randomUUID(), "business-v1", Map.of("amount", 20L));
        var waiting = zeroCost(fixture.request(0, action)); var statuses = new ArrayList<ToolExecutionOutcome.Status>();
        var first = recorder.executeApprovedStart(approvals, transport, waiting, "approval-" + UUID.randomUUID(), statuses::add);
        assertEquals("AWAITING_APPROVAL", first.actionStateObserved()); assertNull(first.ownAttempt());
        assertEquals(List.of(ToolExecutionOutcome.Status.AWAITING_APPROVAL), statuses); assertEquals(0, approvals.calls.get()); assertEquals(0, transport.calls.get());
        assertEquals("SETTLED", startBudgetState(jdbc, waiting)); var oldWait = approvalInvocationFacts(jdbc, waiting);
        var replay = recorder.executeApprovedStart(approvals, transport, waiting, null);
        assertEquals("AWAITING_APPROVAL", replay.actionStateObserved()); assertNull(replay.ownAttempt()); assertEquals(oldWait, approvalInvocationFacts(jdbc, waiting));
        assertEquals(0, approvals.calls.get()); assertEquals(0, transport.calls.get());

        var fresh = zeroCost(fixture.request(1, action)); statuses.clear();
        var completed = recorder.executeApprovedStart(approvals, transport, fresh, "approval-" + UUID.randomUUID(), statuses::add);
        assertEquals("DISPATCHING", completed.actionStateObserved()); var own = completed.ownAttempt(); assertNotNull(own);
        assertEquals("SUCCEEDED", own.state()); assertEquals(ToolExecutionOutcome.Status.SUCCEEDED, own.outcome().status());
        assertEquals(BigDecimal.ZERO, own.outcome().actualCost()); assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, own.accountingState());
        assertEquals(List.of(ToolExecutionOutcome.Status.SUCCEEDED), statuses); assertEquals(1, approvals.calls.get()); assertEquals(1, transport.calls.get());
        assertEquals(1, transport.boundedStarts.get()); assertEquals(0, transport.legacyStarts.get()); assertEquals("SETTLED", startBudgetState(jdbc, fresh));
        assertEquals("APPLIED", approvedActionFacts(jdbc, action).get("state")); assertEquals("SUCCEEDED", invocationState(jdbc, fresh));
        var counters = invocationCounters(jdbc, fresh.binding().run().runId());
        var duplicate = recorder.executeApprovedStart(approvals, transport, fresh, "approval-" + UUID.randomUUID());
        assertEquals("APPLIED", duplicate.actionStateObserved()); assertNotNull(duplicate.ownAttempt()); assertNull(duplicate.ownAttempt().outcome());
        assertEquals(1, approvals.calls.get()); assertEquals(1, transport.calls.get()); assertEquals(counters, invocationCounters(jdbc, fresh.binding().run().runId()));

        var maskedAction = new ToolActionRequest(ACTOR, fixture.descriptor, "approved-masked-" + UUID.randomUUID(), "business-v1", Map.of("amount", 20L));
        var maskedWait = zeroCost(fixture.request(0, maskedAction)); recorder.executeApprovedStart(approvals, transport, maskedWait, null);
        var masked = zeroCost(fixture.request(1, maskedAction)); var maskedApprovals = new ControlledApprovalVerifier(fixture); maskedApprovals.failAfterCallback = true;
        var maskedTransport = new ControlledApprovedStartTransport(jdbc); var observed = new ArrayList<ToolExecutionOutcome.Status>();
        var failure = assertThrows(FixedToolApprovalVerifier.Failure.class,
                () -> recorder.executeApprovedStart(maskedApprovals, maskedTransport, masked, "approval-" + UUID.randomUUID(), observed::add));
        assertEquals(FixedToolApprovalVerifier.FailureKind.APPROVAL_UNAVAILABLE, failure.kind()); assertEquals(List.of(ToolExecutionOutcome.Status.SUCCEEDED), observed);
        assertEquals("APPLIED", approvedActionFacts(jdbc, maskedAction).get("state")); assertEquals("SUCCEEDED", invocationState(jdbc, masked));
        assertEquals(1, maskedApprovals.calls.get()); assertEquals(1, maskedTransport.boundedStarts.get()); assertEquals(0, maskedTransport.legacyStarts.get());
        var maskedFacts = approvalInvocationFacts(jdbc, masked); var maskedBudget = accessBudgetFacts(jdbc, masked.reservationId(), masked.binding().run().runId());
        var metadata = recorder.executeApprovedStart(maskedApprovals, maskedTransport, masked, "approval-" + UUID.randomUUID());
        assertNotNull(metadata.ownAttempt()); assertNull(metadata.ownAttempt().outcome()); assertEquals(maskedFacts, approvalInvocationFacts(jdbc, masked));
        assertEquals(maskedBudget, accessBudgetFacts(jdbc, masked.reservationId(), masked.binding().run().runId()));
        assertEquals(1, maskedApprovals.calls.get()); assertEquals(1, maskedTransport.boundedStarts.get());
    }

    private static ToolInvocationStore.PreparationRequestVO zeroCost(ToolInvocationStore.PreparationRequestVO request) {
        return new ToolInvocationStore.PreparationRequestVO(request.binding(), request.lease(), request.descriptor(), request.arguments(), request.action(),
                request.invocationId(), BigDecimal.ZERO, request.intentExpiry());
    }

    private static final class ControlledApprovalVerifier implements FixedToolApprovalVerifier {
        final ApprovalFixture fixture;
        final AtomicInteger calls = new AtomicInteger();
        boolean failAfterCallback;
        ControlledApprovalVerifier(ApprovalFixture fixture) { this.fixture = fixture; }
        @Override public <T> T withVerifiedApproval(Request request, Callback<T> callback) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); calls.incrementAndGet();
            T value = callback.apply(fixture.observation(request.action(), request.approvalReceiptId(), Duration.ofSeconds(20)));
            if (failAfterCallback) throw new Failure(FailureKind.APPROVAL_UNAVAILABLE); return value;
        }
    }

    private static final class ControlledApprovedStartTransport implements FixedBpmnStartToolTransport {
        final JdbcTemplate jdbc;
        final AtomicInteger calls = new AtomicInteger(), boundedStarts = new AtomicInteger(), legacyStarts = new AtomicInteger();
        ControlledApprovedStartTransport(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); calls.incrementAndGet();
            return callback.apply(new ReadOnlyDecisionToolTransport.VerifiedExecutor(EXECUTOR, request.deadlineAt()), new OneShotStart() {
                @Override public StartObservation start() { legacyStarts.incrementAndGet(); throw new Failure(FailureKind.OPERATION_NOT_SENT); }
                @Override public StartObservation start(Instant callDeadlineAt) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); boundedStarts.incrementAndGet();
                    assertEquals(0, callDeadlineAt.getNano() % 1_000); assertFalse(callDeadlineAt.isAfter(request.deadlineAt()));
                    var fact = jdbc.queryForMap("SELECT state,dispatch_not_after FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?",
                            ACTOR.tenantId(), request.run().runId(), request.invocationId());
                    assertEquals("DISPATCHING", fact.get("state")); assertEquals(callDeadlineAt, ((Timestamp) fact.get("dispatch_not_after")).toInstant());
                    return new StartObservation("tianji:ps-start:v1:" + "a".repeat(64), "controlled-approved-start");
                }
            });
        }
    }

    /** Controlled source projections exercise the real journal and constraints; no live approval or START endpoint is called. */
    private static void approvedWriteJournal(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                             ToolCapabilityStore capabilities) throws Exception {
        var fixture = new ApprovalFixture(source, jdbc, ledger, capabilities); var waiting = fixture.waiting("approved-race-");
        var oldWait = approvalInvocationFacts(jdbc, waiting); var oldBudget = accessBudgetFacts(jdbc, waiting.reservationId(), waiting.binding().run().runId());
        var origin = writeActionFacts(jdbc, waiting.action()); var approval = fixture.observation(waiting.action(), "approval-" + UUID.randomUUID(), Duration.ofSeconds(25));
        var gate = new CountDownLatch(1); var attempts = new ArrayList<ApprovedAttemptVO>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<ApprovedAttemptVO>>();
            for (int index = 0; index < 20; index++) {
                var request = fixture.request(index % 2, waiting.action());
                futures.add(pool.submit(() -> { gate.await(); return fixture.begin(request, approval); }));
            }
            gate.countDown(); for (var future : futures) attempts.add(future.get(20, TimeUnit.SECONDS));
        }
        var winners = attempts.stream().filter(value -> value.result().started()).toList(); assertEquals(1, winners.size()); var winner = winners.getFirst();
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND intent_id=?", Integer.class, ACTOR.tenantId(), UUID.fromString(waiting.action().intentId())));
        assertEquals(oldWait, approvalInvocationFacts(jdbc, waiting)); assertEquals(oldBudget, accessBudgetFacts(jdbc, waiting.reservationId(), waiting.binding().run().runId()));
        int calls = 0, active = 0;
        for (var run : fixture.runs) { var counters = invocationCounters(jdbc, run.run().runId()); calls += ((Number) counters.get("tool_calls_reserved")).intValue(); active += ((Number) counters.get("active_tool_calls")).intValue(); }
        assertEquals(2, calls); assertEquals(1, active);
        for (var attempt : attempts) if (!attempt.result().started()) { assertNull(attempt.result().invocation()); assertNull(attempt.result().budget()); }
        var sourceFact = approvedActionFacts(jdbc, waiting.action()); var codec = new ToolExecutionJsonCodec(); var proof = fixture.proof(approval);
        assertEquals(origin.get("origin_run_id"), sourceFact.get("origin_run_id")); assertEquals(origin.get("expires_at"), sourceFact.get("expires_at"));
        assertEquals(proof, codec.approval((String) sourceFact.get("approval_proof"), (String) sourceFact.get("approval_proof_sha256"), waiting.action()));
        assertEquals(codec.approvalSha256(proof, waiting.action()), sourceFact.get("approval_proof_sha256"));
        assertEquals(approval.approval().expiresAt().truncatedTo(ChronoUnit.MICROS), ((Timestamp) sourceFact.get("approval_expires_at")).toInstant());
        assertNotEquals(0, approval.approval().expiresAt().getNano() % 1_000);
        assertEquals(approval.sourceNamespaceSha256(), sourceFact.get("approval_source_namespace_sha256"));
        assertFalse(fixture.begin(waiting, approval).result().started()); assertEquals(oldWait, approvalInvocationFacts(jdbc, waiting));
        approvalUniqueAndRollback(source, jdbc, fixture, waiting.action(), approval);
        var request = winner.request(); var run = request.binding().run(); var current = ledger.get(ACTOR, new GetAgentRunQuery(run.runId()));
        ledger.finish(ACTOR, request.lease(), current.revision(), new AgentExecutionResult(run.runId(), AgentRunStatusEnum.COMPLETED, Map.of(), null, null, null, null, "UNKNOWN"));
        assertNull(jdbc.queryForObject("SELECT lease_expires_at FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?", Timestamp.class, ACTOR.tenantId(), run.runId()));
        jdbc.update("UPDATE tianji_agent.agent_tool_capability SET issue_epoch=issue_epoch+1,grant_id=?,ciphertext='ENC:v1:test:approved:later',updated_at=clock_timestamp() WHERE tenant_id=? AND run_id=?", "9" + "7".repeat(62), ACTOR.tenantId(), run.runId());
        int authorityCalls = fixture.authorityCalls.get(); var success = approvedSuccess("approved-late-receipt", new BigDecimal("0.5000"));
        var recorded = fixture.store.completeApprovedWrite(request, winner.result().attempt(), winner.nonce(), success);
        assertEquals(success, recorded.outcome()); assertEquals("APPLIED", writeActionFacts(jdbc, request.action()).get("state")); assertEquals(authorityCalls, fixture.authorityCalls.get());
        assertEquals("UNKNOWN", startBudgetState(jdbc, request));
        assertEquals(recorded.invocation(), fixture.store.completeApprovedWrite(request, winner.result().attempt(), winner.nonce(), success).invocation());
        assertEquals(sourceFact.get("approval_proof"), approvedActionFacts(jdbc, request.action()).get("approval_proof"));
        approvalExpiredLeaseAndProof(source, jdbc, ledger, capabilities);
    }

    private static void approvalUniqueAndRollback(DriverManagerDataSource source, JdbcTemplate jdbc, ApprovalFixture fixture,
                                                  ToolActionRequest originalAction, ApprovedObservation originalProof) {
        var original = approvedActionFacts(jdbc, originalAction); var duplicateWait = fixture.waiting("approved-duplicate-");
        var duplicate = fixture.request(1, duplicateWait.action()); var before = approvedActionFacts(jdbc, duplicate.action());
        var counters = fixture.runs.stream().map(run -> invocationCounters(jdbc, run.run().runId())).toList();
        // The controlled projection deliberately repeats a receipt for another coherent action to prove the DB-wide source unique key.
        var repeated = fixture.observation(duplicate.action(), originalProof.approval().approvalReceiptId(), Duration.ofSeconds(20));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> fixture.begin(duplicate, repeated)).code());
        assertEquals(before, approvedActionFacts(jdbc, duplicate.action())); assertEquals(original, approvedActionFacts(jdbc, originalAction));
        assertEquals(counters, fixture.runs.stream().map(run -> invocationCounters(jdbc, run.run().runId())).toList());
        assertNoApprovalAttempt(jdbc, duplicate);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND approval_source_namespace_sha256=? AND approval_receipt_id=?", Integer.class,
                ACTOR.tenantId(), repeated.sourceNamespaceSha256(), repeated.approval().approvalReceiptId()));
        var waiting = fixture.waiting("approved-rollback-"); var request = fixture.request(1, waiting.action()); var snapshot = approvedActionFacts(jdbc, request.action());
        var previous = fixture.runs.stream().map(run -> invocationCounters(jdbc, run.run().runId())).toList();
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_approval_begin_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled approval second-write failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_approval_begin_fail BEFORE UPDATE ON tianji_agent.agent_tool_action_intent FOR EACH ROW WHEN (OLD.state='AWAITING_APPROVAL' AND NEW.state='DISPATCHING') EXECUTE FUNCTION tianji_agent.tool_approval_begin_fail()");
        try {
            var error = assertThrows(AgentLedgerException.class, () -> fixture.begin(request, fixture.observation(request.action(), "approval-" + UUID.randomUUID(), Duration.ofSeconds(20))));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, error.code()); assertNull(error.getCause());
            assertEquals(snapshot, approvedActionFacts(jdbc, request.action())); assertNoApprovalAttempt(jdbc, request);
            assertEquals(previous, fixture.runs.stream().map(run -> invocationCounters(jdbc, run.run().runId())).toList());
        } finally {
            jdbc.execute("DROP TRIGGER tool_approval_begin_fail ON tianji_agent.agent_tool_action_intent"); jdbc.execute("DROP FUNCTION tianji_agent.tool_approval_begin_fail()");
        }
        new ToolExecutionStorageVerifier(source).verify();
        var started = fixture.begin(request, fixture.observation(request.action(), "approval-" + UUID.randomUUID(), Duration.ofSeconds(20)));
        assertTrue(started.result().started());
        assertNull(fixture.store.completeApprovedWrite(request, started.result().attempt(), started.nonce(), approvedSuccess("approved-null-cost", null)).outcome().actualCost());
        assertEquals("RESERVED", startBudgetState(jdbc, request));
    }

    private static void approvalExpiredLeaseAndProof(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                                     ToolCapabilityStore capabilities) throws Exception {
        var fixture = new ApprovalFixture(source, jdbc, ledger, capabilities); var waiting = fixture.waiting("approved-expiry-"); var request = fixture.request(1, waiting.action());
        var proof = fixture.observation(request.action(), "approval-" + UUID.randomUUID(), Duration.ofSeconds(3)); var started = fixture.begin(request, proof);
        assertTrue(started.result().started());
        assertEquals(1, jdbc.update("UPDATE tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=? AND status='RUNNING'", ACTOR.tenantId(), request.binding().run().runId()));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (!now(jdbc).isAfter(proof.expiresAt())) { assertTrue(end - System.nanoTime() > 0, "Controlled approval expiry wait exceeded six seconds"); TimeUnit.MILLISECONDS.sleep(25); }
        int authorityCalls = fixture.authorityCalls.get();
        var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", null);
        assertEquals("UNKNOWN", fixture.store.completeApprovedWrite(request, started.result().attempt(), started.nonce(), unknown).invocation().state());
        var success = approvedSuccess("approved-expired-proof-receipt", new BigDecimal("0.0750"));
        assertEquals(success, fixture.store.completeApprovedWrite(request, started.result().attempt(), started.nonce(), success).outcome());
        assertEquals(authorityCalls, fixture.authorityCalls.get()); assertEquals("RESERVED", startBudgetState(jdbc, request));
        assertEquals(AgentRunStatusEnum.RUNNING, ledger.get(ACTOR, new GetAgentRunQuery(request.binding().run().runId())).status());
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> fixture.store.completeApprovedWrite(request, started.result().attempt(), "2".repeat(64), success)).code());
    }

    private static Map<String, Object> approvedActionFacts(JdbcTemplate jdbc, ToolActionRequest action) {
        return jdbc.queryForMap("SELECT state,revision,origin_run_id,expires_at,dispatch_epoch,dispatch_nonce_sha256,dispatch_run_id,dispatch_invocation_id,executor_issuer,executor_id,approval_source_namespace_sha256,approval_receipt_id,approval_proof_sha256,approval_proof::text AS approval_proof,approval_approved,approval_consumed_at,approval_expires_at,side_effect_outcome,result::text,business_receipt_id,last_error_code,updated_at,closed_at FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?", action.owner().tenantId(), UUID.fromString(action.intentId()));
    }
    private static Map<String, Object> approvalInvocationFacts(JdbcTemplate jdbc, ToolInvocationStore.PreparationRequestVO request) {
        return jdbc.queryForMap("SELECT state,revision,dispatch_epoch,outcome::text,finished_at FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?", ACTOR.tenantId(), request.binding().run().runId(), request.invocationId());
    }
    private static void assertNoApprovalAttempt(JdbcTemplate jdbc, ToolInvocationStore.PreparationRequestVO request) {
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?", Integer.class, ACTOR.tenantId(), request.binding().run().runId(), request.invocationId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?", Integer.class, ACTOR.tenantId(), request.binding().run().runId(), request.reservationId()));
    }
    private static ToolExecutionOutcome approvedSuccess(String receipt, BigDecimal cost) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "controlled-approved-instance"), receipt, null, cost); }
    private record ApprovedAttemptVO(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.ApprovedWriteDispatchBeginResultVO result, String nonce) {
        @Override public String toString() { return "ControlledApprovedAttempt[redacted]"; }
    }
    private static final class ApprovalFixture {
        final ToolDescriptor descriptor = new ToolDescriptor("process.approvedJournal", 1, "approved_journal", "Controlled approved journal",
                DESCRIPTOR.input(), new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start.approved", ProcessClient.START_RESOURCE_KEY, "e".repeat(64), "approval-it-v1"));
        final List<WriteSourceVO> runs = new ArrayList<>();
        final AtomicInteger authorityCalls = new AtomicInteger();
        final ToolExecutionAuthorization authority;
        final ToolInvocationStore store;
        final ToolCapabilityStore capabilities;
        final JdbcTemplate jdbc;
        final Instant expiry;

        ApprovalFixture(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger, ToolCapabilityStore capabilities) {
            this.jdbc = jdbc; this.capabilities = capabilities; expiry = now(jdbc).plusSeconds(120).truncatedTo(ChronoUnit.MICROS);
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(30), 5, 32, 10_000, 10_000,
                    new BigDecimal("100.00"), "USD", 8, Set.of(descriptor.versionKey()), "no-retry", descriptor.policy().approvalPolicyRef());
            ledger.publish(ACTOR, new AgentDefinitionSpecDTO("tool-approved-journal-test", 1, "CODE_FIRST", "controlled-test", "text-v1", "text-v1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(descriptor.reference()), "model-route", "tool-policy-v1"), policy, Map.of()));
            for (int index = 0; index < 2; index++) {
                var queued = ledger.start(ACTOR, new StartAgentRunCommand("tool-approved-journal-test", 1, null, UUID.randomUUID().toString(), null, null, new AgentInputDTO("controlled approved journal", List.of(), Map.of()), null));
                var run = ledger.executionMaterial(ACTOR, new GetAgentRunQuery(queued.runId())).authorization();
                var binding = new ToolExecutionGrant.BindingVO(run, descriptor.reference(), descriptor.policy().bindingSha256(), "TIANJI_TOOL_APPROVED_JOURNAL_TEST_V1", RESOURCE, "tianji.api.agent.start", null);
                var reserved = capabilities.reserve(binding, descriptor).orElseThrow(); assertTrue(capabilities.attach(evidence(reserved, now(jdbc).plusSeconds(120).truncatedTo(ChronoUnit.MICROS)), descriptor));
                var ticket = capabilities.current(binding, descriptor).orElseThrow();
                var claim = ledger.claim(ACTOR, new GetAgentRunQuery(run.runId()), ledger.get(ACTOR, new GetAgentRunQuery(run.runId())).revision(), "approved-test-" + index, Duration.ofMinutes(5));
                runs.add(new WriteSourceVO(run, binding, claim.lease(), ticket));
            }
            authority = new ToolExecutionAuthorization() {
                @Override public Decision authorize(Request request, Boundary boundary) { return check(request, boundary).decision(); }
                @Override public CheckResult check(Request request, Boundary boundary) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); authorityCalls.incrementAndGet();
                    var item = runs.stream().filter(value -> value.run().equals(request.run())).findFirst().orElseThrow(); var ticket = capabilities.current(item.binding(), descriptor).orElseThrow();
                    return new CheckResult(request, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary, Decision.ALLOW, ACTOR.subjectType(), ticket.grantId(), 1L, null, 1L, Instant.now(), ticket.expiresAt()));
                }
            };
            store = new ToolInvocationStore(source, ledger, capabilities, authority); store.verifyStorage();
        }
        ToolInvocationStore.PreparationRequestVO request(int index, ToolActionRequest action) {
            var run = runs.get(index); return new ToolInvocationStore.PreparationRequestVO(run.binding(), run.lease(), descriptor, action.arguments(), action, UUID.randomUUID(), new BigDecimal("0.20"), expiry);
        }
        ToolInvocationStore.PreparationRequestVO waiting(String prefix) {
            var action = new ToolActionRequest(ACTOR, descriptor, prefix + UUID.randomUUID(), "business-v1", Map.of("amount", 20L)); var request = request(0, action);
            assertEquals("AWAITING_APPROVAL", store.prepare(request).invocation().state()); assertEquals("SETTLED", startBudgetState(jdbc, request)); return request;
        }
        ApprovedObservation observation(ToolActionRequest action, String receipt, Duration lifetime) {
            var consumer = TrustedAgentActor.verified(ACTOR.tenantId(), TrustedAgentActor.SubjectType.SERVICE, "approval-it-consumer", "https://idp.example/20");
            var binding = new ToolApprovalBinding(consumer, descriptor.policy().approvalPolicyRef(), "c".repeat(64), "approval-release", "approval_process", "d".repeat(64), "review");
            var query = binding.query(action, receipt); var source = query.sourceStart(); Instant verified = now(jdbc);
            var dto = new VerifiedApprovalDTO(binding.policyRef(), binding.policyBindingSha256(), query.fingerprint(), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), source.commandId(),
                    "controlled-approval-instance", "controlled-approval-task", binding.releaseVersion(), binding.definitionKey(), binding.definitionSha256(), binding.taskDefinitionKey(), OrchestrationRequestDigests.start(source), true, verified.minusSeconds(1), verified, verified.plus(lifetime).plusNanos(987));
            return new ApprovedObservation("process-approval-primary", "https://idp.example/20", "https://process.example/api", binding, dto, consumer, verified.plusSeconds(30));
        }
        ToolExecutionJsonCodec.ApprovalProofVO proof(ApprovedObservation value) { return new ToolExecutionJsonCodec.ApprovalProofVO(1,
                new ToolExecutionJsonCodec.ApprovalSourceVO(value.sourceId(), value.tenantId(), value.sourceIssuer(), value.sourceResource()), value.binding(), value.approval()); }
        ApprovedAttemptVO begin(ToolInvocationStore.PreparationRequestVO request, ApprovedObservation approval) {
            Instant started = Instant.now(); long nanos = System.nanoTime(); var ticket = capabilities.current(request.binding(), descriptor).orElseThrow();
            var executor = new ReadOnlyDecisionToolTransport.VerifiedExecutor(EXECUTOR, ticket.expiresAt()); Instant call = started.plusSeconds(8);
            for (var bound : List.of(request.binding().run().deadlineAt(), ticket.expiresAt(), executor.expiresAt(), approval.expiresAt(), expiry)) if (bound.isBefore(call)) call = bound;
            call = call.truncatedTo(ChronoUnit.MICROS);
            var expected = new ToolExecutionAuthorization.Request(request.binding().run(), request.lease(), descriptor.reference(), descriptor.policy().bindingSha256(), request.parametersSha256(), request.action().intentId(), call);
            var checked = authority.check(expected, ToolExecutionAuthorization.Boundary.DISPATCH); Instant technical = started.plusSeconds(10).isBefore(call) ? started.plusSeconds(10) : call;
            var window = new ToolInvocationStore.DispatchWindowVO(started, nanos, technical, call); String nonce = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            return new ApprovedAttemptVO(request, store.consumeApprovalAndBeginDispatch(request, approval, ticket, checked, executor, ToolInvocationStore.dispatchNonceSha256(nonce), window), nonce);
        }
    }

    private static void knownStartResultAccess(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                                ToolCapabilityStore capabilities) throws Exception {
        var verifier = new ToolExecutionStorageVerifier(source); verifier.verify();
        assertThrows(AgentLedgerException.class, verifier::verifyResultAccess);
        var history = jdbc.queryForList("SELECT version,type,script,checksum FROM tianji_agent.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        var migration = flyway(source, "14"); assertEquals(1, migration.migrate().migrationsExecuted); migration.validate();
        assertEquals(history, jdbc.queryForList("SELECT version,type,script,checksum FROM tianji_agent.flyway_schema_history WHERE version IS NOT NULL AND version<>'14' ORDER BY installed_rank"));
        verifier.verify(); verifier.verifyResultAccess();
        var fixture = new StartRecorderFixture(source, jdbc, ledger, capabilities); var starts = new AtomicInteger();
        var transport = controlledStartTransport(request -> { starts.incrementAndGet(); return startObservation(); }, false);
        var original = fixture.recorder.executeIdempotentStart(transport, fixture.request).ownAttempt().outcome();
        var sourceFact = writeActionFacts(jdbc, fixture.request.action()); var sourceBudget = accessBudgetFacts(jdbc, fixture.request.reservationId(), fixture.run.runId());
        var checks = new AtomicInteger(); var reader = resultAccessReader(source, ledger, capabilities, fixture, checks);
        var access = accessRequest(fixture, UUID.randomUUID()); var gate = new CountDownLatch(1);
        var results = new ArrayList<ToolResultAccessRecorder.StartResultAccessVO>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<ToolResultAccessRecorder.StartResultAccessVO>>();
            for (int index = 0; index < 20; index++) futures.add(pool.submit(() -> { gate.await(); return reader.recordKnownStartResultAccess(access); }));
            gate.countDown(); for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
        }
        for (var result : results) {
            assertEquals(original, result.sourceOutcome()); assertEquals(access.accessId(), result.accessId());
            assertEquals(((Number) sourceFact.get("revision")).longValue(), result.sourceActionRevision());
            assertEquals(results.getFirst().sourceResultSha256(), result.sourceResultSha256());
            assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, result.accessAccountingState());
        }
        assertEquals(20, checks.get()); assertEquals(1, starts.get());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_result_access WHERE tenant_id=? AND run_id=? AND access_id=?", Integer.class,
                ACTOR.tenantId(), fixture.run.runId(), access.accessId()));
        assertEquals(2, ((Number) invocationCounters(jdbc, fixture.run.runId()).get("tool_calls_reserved")).intValue());
        var firstAccess = jdbc.queryForMap("SELECT capability_issue_epoch,authorization_audit::text,authorization_checked_at,authorization_expires_at,created_at FROM tianji_agent.agent_tool_result_access WHERE tenant_id=? AND run_id=? AND access_id=?",
                ACTOR.tenantId(), fixture.run.runId(), access.accessId());
        assertEquals(1, jdbc.update("UPDATE tianji_agent.agent_tool_capability SET issue_epoch=issue_epoch+1,grant_id=?,ciphertext='ENC:v1:test:access:access',updated_at=clock_timestamp() WHERE tenant_id=? AND run_id=?",
                "9" + "8".repeat(62), ACTOR.tenantId(), fixture.run.runId()));
        assertEquals(original, reader.recordKnownStartResultAccess(access).sourceOutcome()); assertEquals(21, checks.get());
        assertEquals(firstAccess, jdbc.queryForMap("SELECT capability_issue_epoch,authorization_audit::text,authorization_checked_at,authorization_expires_at,created_at FROM tianji_agent.agent_tool_result_access WHERE tenant_id=? AND run_id=? AND access_id=?",
                ACTOR.tenantId(), fixture.run.runId(), access.accessId()));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> reader.recordKnownStartResultAccess(accessRequest(fixture, fixture.request.invocationId()))).code());
        var orphan = UUID.randomUUID();
        ledger.budgets().reserveBudget(ACTOR, fixture.request.lease(), new AgentBudgetRequest("tool:" + orphan, AgentBudgetRequest.Kind.TOOL, fixture.request.descriptor().versionKey(), 0, 0, BigDecimal.ZERO));
        ledger.budgets().settleBudget(ACTOR, fixture.request.lease(), "tool:" + orphan, new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> reader.recordKnownStartResultAccess(accessRequest(fixture, orphan))).code());
        resultAccessRollback(source, jdbc, fixture, reader);
        resultAccessAccountingFailure(source, jdbc, fixture, reader, original);
        int count = ((Number) invocationCounters(jdbc, fixture.run.runId()).get("tool_calls_reserved")).intValue();
        for (int index = count; index < 32; index++) {
            String id = "tool:" + UUID.randomUUID();
            ledger.budgets().reserveBudget(ACTOR, fixture.request.lease(), new AgentBudgetRequest(id, AgentBudgetRequest.Kind.TOOL, fixture.request.descriptor().versionKey(), 0, 0, BigDecimal.ZERO));
            ledger.budgets().settleBudget(ACTOR, fixture.request.lease(), id, new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO));
        }
        assertEquals(original, reader.recordKnownStartResultAccess(access).sourceOutcome());
        assertEquals(32, ((Number) invocationCounters(jdbc, fixture.run.runId()).get("tool_calls_reserved")).intValue());
        var full = accessRequest(fixture, UUID.randomUUID());
        assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, assertThrows(AgentLedgerException.class, () -> reader.recordKnownStartResultAccess(full)).code());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_result_access WHERE tenant_id=? AND run_id=? AND access_id=?", Integer.class,
                ACTOR.tenantId(), fixture.run.runId(), full.accessId()));
        assertEquals(32, ((Number) invocationCounters(jdbc, fixture.run.runId()).get("tool_calls_reserved")).intValue());
        assertEquals(sourceFact, writeActionFacts(jdbc, fixture.request.action())); assertEquals(sourceBudget, accessBudgetFacts(jdbc, fixture.request.reservationId(), fixture.run.runId()));
        assertEquals(1, starts.get());
        for (var kind : List.of(FixedBpmnStartToolTransport.FailureKind.OPERATION_DENIED, FixedBpmnStartToolTransport.FailureKind.OPERATION_NOT_SENT)) {
            var negative = new StartRecorderFixture(source, jdbc, ledger, capabilities);
            var known = negative.recorder.executeIdempotentStart(controlledStartTransport(request -> { throw new FixedBpmnStartToolTransport.Failure(kind); }, false), negative.request).ownAttempt().outcome();
            var projection = resultAccessReader(source, ledger, capabilities, negative, new AtomicInteger()).recordKnownStartResultAccess(accessRequest(negative, UUID.randomUUID()));
            assertEquals(kind == FixedBpmnStartToolTransport.FailureKind.OPERATION_DENIED ? ToolExecutionOutcome.Status.REJECTED : ToolExecutionOutcome.Status.FAILED_KNOWN, known.status());
            assertEquals(known, projection.sourceOutcome()); assertTrue(projection.sourceOutcome().output().isEmpty()); assertNull(projection.sourceOutcome().businessReceiptId());
        }
    }

    private static void resultAccessRollback(DriverManagerDataSource source, JdbcTemplate jdbc, StartRecorderFixture fixture, ToolResultAccessRecorder reader) {
        var request = accessRequest(fixture, UUID.randomUUID()); var counters = invocationCounters(jdbc, fixture.run.runId());
        var before = writeActionFacts(jdbc, fixture.request.action());
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_access_insert_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled access insert failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_access_insert_fail BEFORE INSERT ON tianji_agent.agent_tool_result_access FOR EACH ROW EXECUTE FUNCTION tianji_agent.tool_access_insert_fail()");
        try {
            var failure = assertThrows(AgentLedgerException.class, () -> reader.recordKnownStartResultAccess(request)); assertNull(failure.getCause());
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertEquals(counters, invocationCounters(jdbc, fixture.run.runId()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_result_access WHERE tenant_id=? AND run_id=? AND access_id=?", Integer.class,
                    ACTOR.tenantId(), fixture.run.runId(), request.accessId()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?", Integer.class,
                    ACTOR.tenantId(), fixture.run.runId(), request.reservationId()));
            assertEquals(before, writeActionFacts(jdbc, fixture.request.action()));
        } finally {
            jdbc.execute("DROP TRIGGER tool_access_insert_fail ON tianji_agent.agent_tool_result_access"); jdbc.execute("DROP FUNCTION tianji_agent.tool_access_insert_fail()");
        }
        new ToolExecutionStorageVerifier(source).verifyResultAccess();
    }

    private static void resultAccessAccountingFailure(DriverManagerDataSource source, JdbcTemplate jdbc, StartRecorderFixture fixture,
                                                       ToolResultAccessRecorder reader, ToolExecutionOutcome original) {
        var request = accessRequest(fixture, UUID.randomUUID());
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_access_account_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled access accounting failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_access_account_fail BEFORE UPDATE ON tianji_agent.agent_budget_reservation FOR EACH ROW WHEN (OLD.state='RESERVED' AND NEW.state='SETTLED') EXECUTE FUNCTION tianji_agent.tool_access_account_fail()");
        try {
            var result = reader.recordKnownStartResultAccess(request); assertEquals(original, result.sourceOutcome());
            assertEquals(ToolInvocationRecorder.AccountingState.PENDING, result.accessAccountingState());
            assertEquals("RESERVED", accessBudgetFacts(jdbc, request.reservationId(), fixture.run.runId()).get("state"));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_result_access WHERE tenant_id=? AND run_id=? AND access_id=?", Integer.class,
                    ACTOR.tenantId(), fixture.run.runId(), request.accessId()));
        } finally {
            jdbc.execute("DROP TRIGGER tool_access_account_fail ON tianji_agent.agent_budget_reservation"); jdbc.execute("DROP FUNCTION tianji_agent.tool_access_account_fail()");
        }
        new ToolExecutionStorageVerifier(source).verifyResultAccess();
        var counters = ((Number) invocationCounters(jdbc, fixture.run.runId()).get("tool_calls_reserved")).intValue();
        var replay = reader.recordKnownStartResultAccess(request); assertEquals(original, replay.sourceOutcome());
        assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, replay.accessAccountingState());
        assertEquals(counters, ((Number) invocationCounters(jdbc, fixture.run.runId()).get("tool_calls_reserved")).intValue());
    }

    private static ToolResultAccessRecorder resultAccessReader(DriverManagerDataSource source, AgentLedgerService ledger, ToolCapabilityStore capabilities,
                                                               StartRecorderFixture fixture, AtomicInteger checks) {
        var authority = new ToolExecutionAuthorization() {
            @Override public Decision authorize(Request request, Boundary boundary) { return check(request, boundary).decision(); }
            @Override public CheckResult check(Request request, Boundary boundary) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertEquals(Boundary.RECONCILE, boundary); assertEquals(fixture.run, request.run());
                assertEquals(fixture.request.parametersSha256(), request.parametersSha256()); assertNotEquals(fixture.request.action().requestSha256(), request.parametersSha256());
                var ticket = capabilities.current(fixture.request.binding(), fixture.request.descriptor()).orElseThrow(); checks.incrementAndGet();
                return new CheckResult(request, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary, Decision.ALLOW,
                        ACTOR.subjectType(), ticket.grantId(), 1L, null, 1L, Instant.now(), ticket.expiresAt()));
            }
        };
        var store = new ToolResultAccessStore(source, ledger, capabilities, authority); store.verifyStorage();
        return new ToolResultAccessRecorder(store, ledger);
    }
    private static ToolResultAccessStore.StartResultAccessRequestVO accessRequest(StartRecorderFixture fixture, UUID id) {
        return new ToolResultAccessStore.StartResultAccessRequestVO(fixture.request.binding(), fixture.request.lease(), fixture.request.descriptor(), fixture.request.action(), id);
    }
    private static Map<String, Object> accessBudgetFacts(JdbcTemplate jdbc, String id, String runId) {
        return jdbc.queryForMap("SELECT state,estimated_cost,actual_cost,actual_input_tokens,actual_output_tokens,lease_epoch FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?",
                ACTOR.tenantId(), runId, id);
    }

    private static void fixedStartRecording(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                             ToolCapabilityStore capabilities) {
        var fixture = new StartRecorderFixture(source, jdbc, ledger, capabilities); var calls = new AtomicInteger();
        var transport = controlledStartTransport(request -> {
            calls.incrementAndGet(); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("DISPATCHING", invocationState(jdbc, fixture.request)); assertEquals("DISPATCHING", writeActionFacts(jdbc, fixture.request.action()).get("state"));
            assertEquals("RESERVED", startBudgetState(jdbc, fixture.request)); return startObservation();
        }, false);
        var result = fixture.recorder.executeIdempotentStart(transport, fixture.request);
        assertNotNull(result); assertEquals(fixture.request.action().intentId(), result.actionIntentId());
        assertEquals("PREPARED", result.actionStateAtPreparation()); assertEquals(1, result.actionRevisionAtPreparation());
        var own = result.ownAttempt(); assertNotNull(own); assertEquals("SUCCEEDED", own.state()); assertTrue(own.operationInvokedThisCall());
        assertEquals(Map.of("instanceId", "controlled-start-instance"), own.outcome().output());
        assertEquals(startObservation().hostReceiptReference(), own.outcome().businessReceiptId());
        assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, own.accountingState());
        assertEquals("APPLIED", writeActionFacts(jdbc, fixture.request.action()).get("state"));
        assertEquals("SETTLED", startBudgetState(jdbc, fixture.request));
        assertEquals(0, ((Number) invocationCounters(jdbc, fixture.run.runId()).get("active_tool_calls")).intValue());
        var replay = fixture.recorder.executeIdempotentStart(transport, fixture.request);
        assertNotNull(replay.ownAttempt()); assertEquals("SUCCEEDED", replay.ownAttempt().state()); assertNull(replay.ownAttempt().outcome());
        assertFalse(replay.ownAttempt().operationInvokedThisCall());
        var another = fixture.recorder.executeIdempotentStart(transport, fixture.newInvocation());
        assertNull(another.ownAttempt()); assertEquals(1, calls.get());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=?", Integer.class, ACTOR.tenantId(), fixture.run.runId()));
        startOuterFailure(source, jdbc, ledger, capabilities);
        startAccountingFailure(source, jdbc, ledger, capabilities);
    }

    private static void startOuterFailure(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                           ToolCapabilityStore capabilities) {
        var fixture = new StartRecorderFixture(source, jdbc, ledger, capabilities); var calls = new AtomicInteger();
        var transport = controlledStartTransport(request -> { calls.incrementAndGet(); return startObservation(); }, true);
        var failure = assertThrows(FixedBpmnStartToolTransport.Failure.class, () -> fixture.recorder.executeIdempotentStart(transport, fixture.request));
        assertEquals(FixedBpmnStartToolTransport.FailureKind.RELEASE_CHANGED, failure.kind()); assertNull(failure.getCause());
        assertEquals("SUCCEEDED", invocationState(jdbc, fixture.request));
        var action = writeActionFacts(jdbc, fixture.request.action()); assertEquals("APPLIED", action.get("state"));
        assertEquals(startObservation().hostReceiptReference(), action.get("business_receipt_id"));
        var replay = fixture.recorder.executeIdempotentStart(transport, fixture.request);
        assertNotNull(replay.ownAttempt()); assertNull(replay.ownAttempt().outcome()); assertFalse(replay.ownAttempt().operationInvokedThisCall());
        assertEquals(action, writeActionFacts(jdbc, fixture.request.action())); assertEquals(1, calls.get());
    }

    private static void startAccountingFailure(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                                ToolCapabilityStore capabilities) {
        var fixture = new StartRecorderFixture(source, jdbc, ledger, capabilities); var calls = new AtomicInteger();
        var transport = controlledStartTransport(request -> { calls.incrementAndGet(); return startObservation(); }, false);
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_start_accounting_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled start accounting failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_start_accounting_fail BEFORE UPDATE ON tianji_agent.agent_budget_reservation FOR EACH ROW WHEN (OLD.state='RESERVED' AND NEW.state='SETTLED') EXECUTE FUNCTION tianji_agent.tool_start_accounting_fail()");
        try {
            var result = fixture.recorder.executeIdempotentStart(transport, fixture.request);
            assertEquals("PREPARED", result.actionStateAtPreparation()); assertNotNull(result.ownAttempt().outcome());
            assertEquals("SUCCEEDED", result.ownAttempt().state()); assertEquals(ToolInvocationRecorder.AccountingState.PENDING, result.ownAttempt().accountingState());
            assertEquals("APPLIED", writeActionFacts(jdbc, fixture.request.action()).get("state"));
            assertEquals("RESERVED", startBudgetState(jdbc, fixture.request));
        } finally {
            jdbc.execute("DROP TRIGGER tool_start_accounting_fail ON tianji_agent.agent_budget_reservation"); jdbc.execute("DROP FUNCTION tianji_agent.tool_start_accounting_fail()");
        }
        new ToolExecutionStorageVerifier(source).verify();
        var replay = fixture.recorder.executeIdempotentStart(transport, fixture.request);
        assertEquals("SUCCEEDED", replay.ownAttempt().state()); assertNull(replay.ownAttempt().outcome());
        assertFalse(replay.ownAttempt().operationInvokedThisCall()); assertEquals(ToolInvocationRecorder.AccountingState.PENDING, replay.ownAttempt().accountingState());
        assertEquals("RESERVED", startBudgetState(jdbc, fixture.request));
        assertEquals(1, ((Number) invocationCounters(jdbc, fixture.run.runId()).get("active_tool_calls")).intValue()); assertEquals(1, calls.get());
    }

    private static FixedBpmnStartToolTransport controlledStartTransport(Function<FixedBpmnStartToolTransport.Request, FixedBpmnStartToolTransport.StartObservation> operation,
                                                                        boolean failAfterCallback) {
        return new FixedBpmnStartToolTransport() {
            @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                T result = callback.apply(new ReadOnlyDecisionToolTransport.VerifiedExecutor(EXECUTOR, request.deadlineAt()), () -> operation.apply(request));
                if (failAfterCallback) throw new Failure(FailureKind.RELEASE_CHANGED);
                return result;
            }
        };
    }
    private static FixedBpmnStartToolTransport.StartObservation startObservation() {
        return new FixedBpmnStartToolTransport.StartObservation("tianji:ps-start:v1:" + "c".repeat(64), "controlled-start-instance");
    }
    private static String startBudgetState(JdbcTemplate jdbc, ToolInvocationStore.PreparationRequestVO request) {
        return jdbc.queryForObject("SELECT state FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?",
                String.class, ACTOR.tenantId(), request.binding().run().runId(), request.reservationId());
    }
    private static final class StartRecorderFixture {
        final AgentRunAuthorization.Request run;
        final ToolInvocationStore.PreparationRequestVO request;
        final ToolInvocationRecorder recorder;

        StartRecorderFixture(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger, ToolCapabilityStore capabilities) {
            var descriptor = new ToolDescriptor("process.recordedStart", 1, "recorded_start", "Controlled fixed START recorder",
                    DESCRIPTOR.input(), new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                    new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.recorded", ProcessClient.START_RESOURCE_KEY, "b".repeat(64), null));
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(30), 5, 32, 10_000, 10_000,
                    null, null, 8, Set.of(descriptor.versionKey()), "no-retry", null);
            ledger.publish(ACTOR, new AgentDefinitionSpecDTO("tool-start-recorder-test", 1, "CODE_FIRST", "controlled-test", "text-v1", "text-v1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(descriptor.reference()), "model-route", "tool-policy-v1"), policy, Map.of()));
            var queued = ledger.start(ACTOR, new StartAgentRunCommand("tool-start-recorder-test", 1, null, UUID.randomUUID().toString(), null, null,
                    new AgentInputDTO("controlled fixed START recorder", List.of(), Map.of()), null));
            run = ledger.executionMaterial(ACTOR, new GetAgentRunQuery(queued.runId())).authorization();
            var binding = new ToolExecutionGrant.BindingVO(run, descriptor.reference(), descriptor.policy().bindingSha256(), "TIANJI_TOOL_START_RECORDER_TEST_V1", RESOURCE, "tianji.api.agent.start", null);
            var reserved = capabilities.reserve(binding, descriptor).orElseThrow();
            assertTrue(capabilities.attach(evidence(reserved, now(jdbc).plusSeconds(120).truncatedTo(ChronoUnit.MICROS)), descriptor));
            var ticket = capabilities.current(binding, descriptor).orElseThrow();
            var claim = ledger.claim(ACTOR, new GetAgentRunQuery(run.runId()), ledger.get(ACTOR, new GetAgentRunQuery(run.runId())).revision(), "start-recorder-test", Duration.ofMinutes(5));
            var authority = new ToolExecutionAuthorization() {
                @Override public Decision authorize(Request value, Boundary boundary) { return check(value, boundary).decision(); }
                @Override public CheckResult check(Request value, Boundary boundary) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertEquals(run, value.run());
                    return new CheckResult(value, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary, Decision.ALLOW,
                            ACTOR.subjectType(), ticket.grantId(), 1L, null, 1L, Instant.now(), ticket.expiresAt()));
                }
            };
            var preparations = new ToolInvocationStore(source, ledger, capabilities, authority); preparations.verifyStorage();
            recorder = new ToolInvocationRecorder(source, ledger, preparations, capabilities, authority); recorder.verifyStorage();
            var action = new ToolActionRequest(ACTOR, descriptor, "fixed-start-recorder-" + UUID.randomUUID(), "business-v1", Map.of("amount", 20L));
            request = new ToolInvocationStore.PreparationRequestVO(binding, claim.lease(), descriptor, action.arguments(), action, UUID.randomUUID(), BigDecimal.ZERO, now(jdbc).plusSeconds(600));
        }
        ToolInvocationStore.PreparationRequestVO newInvocation() {
            return new ToolInvocationStore.PreparationRequestVO(request.binding(), request.lease(), request.descriptor(), request.arguments(), request.action(), UUID.randomUUID(), BigDecimal.ZERO, request.intentExpiry());
        }
    }

    /** Controlled journal calls only: no transport, native tool callback or real identity authority is used here. */
    private static void idempotentWriteJournal(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                                ToolCapabilityStore capabilities) throws Exception {
        var fixture = new WriteFixture(source, jdbc, ledger, capabilities);
        var action = fixture.action("write-race-"); var requests = new ArrayList<ToolInvocationStore.PreparationRequestVO>();
        for (int index = 0; index < 20; index++) requests.add(fixture.request(index % 2, action));
        var gate = new CountDownLatch(1); var prepared = new ArrayList<ToolInvocationStore.WritePreparationResultVO>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<ToolInvocationStore.WritePreparationResultVO>>();
            for (var request : requests) futures.add(pool.submit(() -> { gate.await(); return fixture.store.prepareOrLoadIdempotentWrite(request); }));
            gate.countDown();
            for (var future : futures) { var result = future.get(20, TimeUnit.SECONDS); assertNotNull(result.invocation()); prepared.add(result); }
        }
        assertEquals(1, count(jdbc, action));
        assertEquals(20, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND intent_id=?", Integer.class, ACTOR.tenantId(), UUID.fromString(action.intentId())));
        var origin = writeActionFacts(jdbc, action);
        for (var result : prepared) { assertEquals("PREPARED", result.invocation().state()); assertEquals(prepared.getFirst().action(), result.action()); }
        assertEquals(fixture.expiry, prepared.getFirst().action().expiresAt());
        var beginGate = new CountDownLatch(1); var attempts = new ArrayList<WriteAttemptVO>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<WriteAttemptVO>>();
            for (int index = 0; index < requests.size(); index++) {
                var request = requests.get(index); var preparation = prepared.get(index).invocation();
                futures.add(pool.submit(() -> { beginGate.await(); return fixture.begin(request, preparation); }));
            }
            beginGate.countDown(); for (var future : futures) attempts.add(future.get(20, TimeUnit.SECONDS));
        }
        var winners = attempts.stream().filter(value -> value.result().started()).toList(); assertEquals(1, winners.size());
        var winner = winners.getFirst(); var current = writeActionFacts(jdbc, action);
        assertEquals("DISPATCHING", current.get("state")); assertEquals("UNKNOWN", current.get("side_effect_outcome"));
        assertEquals(origin.get("origin_run_id"), current.get("origin_run_id")); assertEquals(origin.get("expires_at"), current.get("expires_at"));
        assertEquals(winner.request().binding().run().runId(), current.get("dispatch_run_id")); assertEquals(winner.request().invocationId(), current.get("dispatch_invocation_id"));
        assertEquals(winner.result().attempt().dispatchNonceSha256(), current.get("dispatch_nonce_sha256"));
        for (var attempt : attempts) if (!attempt.result().started()) {
            assertEquals("FAILED_KNOWN", invocationState(jdbc, attempt.request()));
            var outcome = new ToolExecutionJsonCodec().outcome(jdbc.queryForObject(
                    "SELECT outcome::text FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?",
                    String.class, ACTOR.tenantId(), attempt.request().binding().run().runId(), attempt.request().invocationId()), DESCRIPTOR);
            assertEquals("ACTION_NOT_DISPATCHABLE", outcome.errorCode()); assertEquals(0, outcome.actualCost().compareTo(new BigDecimal("0.0100")));
        }
        for (var item : fixture.runs) {
            var counters = invocationCounters(jdbc, item.run().runId());
            assertEquals(10, ((Number) counters.get("tool_calls_reserved")).intValue()); assertEquals(10, ((Number) counters.get("active_tool_calls")).intValue());
            assertEquals(0, ((BigDecimal) counters.get("estimated_cost_reserved")).compareTo(new BigDecimal("2.00")));
            assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND state='RESERVED'", Integer.class, ACTOR.tenantId(), item.run().runId()));
        }
        writeLateFact(jdbc, ledger, fixture, winner);
        writePairRollback(source, jdbc, ledger, capabilities);
        writeFuturePointer(source, jdbc, ledger, capabilities);
    }

    private static void writeLateFact(JdbcTemplate jdbc, AgentLedgerService ledger, WriteFixture fixture, WriteAttemptVO attempt) {
        var request = attempt.request(); var run = request.binding().run(); var current = ledger.get(ACTOR, new GetAgentRunQuery(run.runId()));
        ledger.finish(ACTOR, request.lease(), current.revision(), new AgentExecutionResult(run.runId(), AgentRunStatusEnum.COMPLETED,
                Map.of(), null, null, null, null, "UNKNOWN"));
        jdbc.update("UPDATE tianji_agent.agent_tool_capability SET issue_epoch=issue_epoch+1,grant_id=?,ciphertext='ENC:v1:test:later:later',updated_at=clock_timestamp() WHERE tenant_id=? AND run_id=?",
                "9" + "3".repeat(62), ACTOR.tenantId(), run.runId());
        int checks = fixture.authorityCalls.get();
        var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", null);
        assertEquals("UNKNOWN", fixture.store.completeIdempotentWrite(request, attempt.result().attempt(), attempt.nonce(), unknown).invocation().state());
        var success = writeSuccess("late-write-receipt", new BigDecimal("0.5000"));
        ToolInvocationStore.RecordedResultVO recorded;
        Thread.currentThread().interrupt();
        try {
            recorded = fixture.store.completeIdempotentWrite(request, attempt.result().attempt(), attempt.nonce(), success);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals("SUCCEEDED", recorded.invocation().state()); assertEquals(success.businessReceiptId(), recorded.outcome().businessReceiptId());
        assertEquals("APPLIED", writeActionFacts(jdbc, request.action()).get("state")); assertEquals(checks, fixture.authorityCalls.get());
        assertEquals(AgentRunStatusEnum.COMPLETED, ledger.get(ACTOR, new GetAgentRunQuery(run.runId())).status());
        assertEquals("UNKNOWN", jdbc.queryForObject("SELECT state FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?",
                String.class, ACTOR.tenantId(), run.runId(), request.reservationId()));
        assertEquals(recorded.invocation(), fixture.store.completeIdempotentWrite(request, attempt.result().attempt(), attempt.nonce(), success).invocation());
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> fixture.store.completeIdempotentWrite(request, attempt.result().attempt(), attempt.nonce(), writeSuccess("different-write-receipt", BigDecimal.ZERO))).code());
    }

    private static void writePairRollback(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                           ToolCapabilityStore capabilities) {
        var fixture = new WriteFixture(source, jdbc, ledger, capabilities); var request = fixture.request(0, fixture.action("write-rollback-"));
        var prepared = fixture.store.prepareOrLoadIdempotentWrite(request); var before = writeActionFacts(jdbc, request.action());
        var counters = invocationCounters(jdbc, request.binding().run().runId());
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_write_begin_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled write begin failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_write_begin_fail BEFORE UPDATE ON tianji_agent.agent_tool_action_intent FOR EACH ROW WHEN (NEW.state='DISPATCHING') EXECUTE FUNCTION tianji_agent.tool_write_begin_fail()");
        try {
            var error = assertThrows(AgentLedgerException.class, () -> fixture.begin(request, prepared.invocation()));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, error.code()); assertNull(error.getCause());
            assertEquals("PREPARED", invocationState(jdbc, request)); assertEquals(before, writeActionFacts(jdbc, request.action()));
            assertEquals(0, jdbc.queryForObject("SELECT dispatch_epoch FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?",
                    Integer.class, ACTOR.tenantId(), request.binding().run().runId(), request.invocationId()));
            assertEquals(counters, invocationCounters(jdbc, request.binding().run().runId()));
        } finally {
            jdbc.execute("DROP TRIGGER tool_write_begin_fail ON tianji_agent.agent_tool_action_intent"); jdbc.execute("DROP FUNCTION tianji_agent.tool_write_begin_fail()");
        }
        new ToolExecutionStorageVerifier(source).verify();
        var started = fixture.begin(request, prepared.invocation()); assertTrue(started.result().started()); var dispatched = writeActionFacts(jdbc, request.action());
        var success = writeSuccess("rollback-receipt", new BigDecimal("0.4000"));
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_write_complete_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled write completion failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_write_complete_fail BEFORE UPDATE ON tianji_agent.agent_tool_action_intent FOR EACH ROW WHEN (NEW.state='APPLIED') EXECUTE FUNCTION tianji_agent.tool_write_complete_fail()");
        try {
            var error = assertThrows(AgentLedgerException.class, () -> fixture.store.completeIdempotentWrite(request, started.result().attempt(), started.nonce(), success));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, error.code()); assertNull(error.getCause());
            assertEquals("DISPATCHING", invocationState(jdbc, request)); assertEquals(dispatched, writeActionFacts(jdbc, request.action()));
            assertNull(jdbc.queryForObject("SELECT outcome::text FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?", String.class,
                    ACTOR.tenantId(), request.binding().run().runId(), request.invocationId()));
            assertEquals(counters, invocationCounters(jdbc, request.binding().run().runId()));
        } finally {
            jdbc.execute("DROP TRIGGER tool_write_complete_fail ON tianji_agent.agent_tool_action_intent"); jdbc.execute("DROP FUNCTION tianji_agent.tool_write_complete_fail()");
        }
        new ToolExecutionStorageVerifier(source).verify();
        assertEquals("SUCCEEDED", fixture.store.completeIdempotentWrite(request, started.result().attempt(), started.nonce(), success).invocation().state());
        assertEquals("APPLIED", writeActionFacts(jdbc, request.action()).get("state"));
        assertEquals(counters, invocationCounters(jdbc, request.binding().run().runId()));
    }

    private static void writeFuturePointer(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                            ToolCapabilityStore capabilities) {
        var fixture = new WriteFixture(source, jdbc, ledger, capabilities); var action = fixture.action("write-future-");
        var original = fixture.request(0, action); var later = fixture.request(1, action);
        var first = fixture.store.prepareOrLoadIdempotentWrite(original); var second = fixture.store.prepareOrLoadIdempotentWrite(later);
        var started = fixture.begin(original, first.invocation()); assertTrue(started.result().started());
        var laterCheck = fixture.check(later, second.invocation()); var laterNonce = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        var laterSha = ToolInvocationStore.dispatchNonceSha256(laterNonce); var audit = new ToolExecutionJsonCodec().audit(laterCheck.checked().audit());
        // Model a fully valid later generation in the frozen schema, without adding any redrive API.
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> {
            jdbc.queryForList("SELECT intent_id FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=? FOR UPDATE", ACTOR.tenantId(), UUID.fromString(action.intentId()));
            Timestamp time = Timestamp.from(now(jdbc).truncatedTo(ChronoUnit.MICROS));
            assertEquals(1, jdbc.update("UPDATE tianji_agent.agent_tool_invocation SET state='DISPATCHING',revision=revision+1,dispatch_epoch=2,dispatch_nonce_sha256=?,executor_issuer=?,executor_id=?,authorization_audit=CAST(? AS jsonb),side_effect_outcome='UNKNOWN',updated_at=?,dispatched_at=? WHERE tenant_id=? AND run_id=? AND invocation_id=? AND state='PREPARED'",
                    laterSha, EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId(), audit, time, time, ACTOR.tenantId(), later.binding().run().runId(), later.invocationId()));
            assertEquals(1, jdbc.update("UPDATE tianji_agent.agent_tool_action_intent SET revision=revision+1,dispatch_epoch=2,dispatch_nonce_sha256=?,dispatch_run_id=?,dispatch_invocation_id=?,executor_issuer=?,executor_id=?,updated_at=? WHERE tenant_id=? AND intent_id=? AND state='DISPATCHING'",
                    laterSha, later.binding().run().runId(), later.invocationId(), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId(), time, ACTOR.tenantId(), UUID.fromString(action.intentId())));
        });
        var current = writeActionFacts(jdbc, action); int checks = fixture.authorityCalls.get();
        var result = fixture.store.completeIdempotentWrite(original, started.result().attempt(), started.nonce(), writeSuccess("old-generation-receipt", new BigDecimal("0.3500")));
        assertEquals("SUCCEEDED", result.invocation().state()); assertEquals(current, writeActionFacts(jdbc, action));
        assertEquals("DISPATCHING", invocationState(jdbc, later)); assertEquals(checks, fixture.authorityCalls.get());
        assertEquals(later.invocationId(), current.get("dispatch_invocation_id")); assertEquals(2, ((Number) current.get("dispatch_epoch")).intValue());
    }

    private static Map<String, Object> writeActionFacts(JdbcTemplate jdbc, ToolActionRequest action) {
        return jdbc.queryForMap("SELECT state,revision,origin_run_id,expires_at,dispatch_run_id,dispatch_invocation_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id,side_effect_outcome,result::text,business_receipt_id,last_error_code,updated_at,closed_at FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?",
                action.owner().tenantId(), UUID.fromString(action.intentId()));
    }
    private static ToolExecutionOutcome writeSuccess(String receipt, BigDecimal actualCost) {
        return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "confirmed"), receipt, null, actualCost);
    }
    private record WriteSourceVO(AgentRunAuthorization.Request run, ToolExecutionGrant.BindingVO binding, AgentRunLease lease, ToolExecutionGrant.EvidenceVO evidence) { }
    private record WriteCheckVO(ToolExecutionGrant.EvidenceVO evidence, ToolExecutionAuthorization.CheckResult checked,
                                ReadOnlyDecisionToolTransport.VerifiedExecutor executor, ToolInvocationStore.DispatchWindowVO window) { }
    private record WriteAttemptVO(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.WriteDispatchBeginResultVO result, String nonce) {
        @Override public String toString() { return "ControlledWriteAttempt[redacted]"; }
    }
    private static final class WriteFixture {
        final ToolInvocationStore store;
        final List<WriteSourceVO> runs = new ArrayList<>();
        final AtomicInteger authorityCalls = new AtomicInteger();
        final ToolExecutionAuthorization authority;
        final ToolCapabilityStore capabilities;
        final Instant expiry;

        WriteFixture(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger, ToolCapabilityStore capabilities) {
            this.capabilities = capabilities; expiry = now(jdbc).plusSeconds(120).truncatedTo(ChronoUnit.MICROS);
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(60), 5, 64, 10_000, 10_000,
                    new BigDecimal("100.00"), "USD", 32, Set.of(DESCRIPTOR.versionKey()), "no-retry", null);
            ledger.publish(ACTOR, new AgentDefinitionSpecDTO("tool-idw-journal-test", 1, "CODE_FIRST", "controlled-test", "text-v1", "text-v1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(DESCRIPTOR.reference()), "model-route", "tool-policy-v1"), policy, Map.of()));
            for (int index = 0; index < 2; index++) {
                var queued = ledger.start(ACTOR, new StartAgentRunCommand("tool-idw-journal-test", 1, null, UUID.randomUUID().toString(), null, null,
                        new AgentInputDTO("controlled idempotent write journal", List.of(), Map.of()), null));
                var run = ledger.executionMaterial(ACTOR, new GetAgentRunQuery(queued.runId())).authorization(); var binding = binding(run);
                var reservation = capabilities.reserve(binding, DESCRIPTOR).orElseThrow();
                assertTrue(capabilities.attach(evidence(reservation, now(jdbc).plusSeconds(240).truncatedTo(ChronoUnit.MICROS)), DESCRIPTOR));
                var ticket = capabilities.current(binding, DESCRIPTOR).orElseThrow();
                var claim = ledger.claim(ACTOR, new GetAgentRunQuery(run.runId()), ledger.get(ACTOR, new GetAgentRunQuery(run.runId())).revision(), "idw-test-" + index, Duration.ofMinutes(5));
                runs.add(new WriteSourceVO(run, binding, claim.lease(), ticket));
            }
            authority = new ToolExecutionAuthorization() {
                @Override public Decision authorize(Request request, Boundary boundary) { return check(request, boundary).decision(); }
                @Override public CheckResult check(Request request, Boundary boundary) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); authorityCalls.incrementAndGet();
                    var item = runs.stream().filter(value -> value.run().equals(request.run())).findFirst().orElseThrow();
                    return new CheckResult(request, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary, Decision.ALLOW,
                            ACTOR.subjectType(), item.evidence().grantId(), 1L, null, 1L, Instant.now(), item.evidence().expiresAt()));
                }
            };
            store = new ToolInvocationStore(source, ledger, capabilities, authority); store.verifyStorage();
        }
        ToolActionRequest action(String prefix) { return new ToolActionRequest(ACTOR, DESCRIPTOR, prefix + UUID.randomUUID(), "business-v1", Map.of("amount", 20L)); }
        ToolInvocationStore.PreparationRequestVO request(int runIndex, ToolActionRequest action) {
            var item = runs.get(runIndex);
            return new ToolInvocationStore.PreparationRequestVO(item.binding(), item.lease(), DESCRIPTOR, action.arguments(), action, UUID.randomUUID(), new BigDecimal("0.20"), expiry);
        }
        WriteCheckVO check(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.RecorderPreparationVO prepared) {
            Instant started = Instant.now(); long nanos = System.nanoTime(); var ticket = capabilities.current(request.binding(), DESCRIPTOR).orElseThrow();
            var executor = new ReadOnlyDecisionToolTransport.VerifiedExecutor(EXECUTOR, ticket.expiresAt());
            Instant deadline = prepared.dispatchNotAfter();
            for (var bound : List.of(request.binding().run().deadlineAt(), ticket.expiresAt(), executor.expiresAt())) if (bound.isBefore(deadline)) deadline = bound;
            var expected = new ToolExecutionAuthorization.Request(request.binding().run(), request.lease(), DESCRIPTOR.reference(), DESCRIPTOR.policy().bindingSha256(),
                    request.parametersSha256(), request.action().intentId(), deadline);
            var checked = authority.check(expected, ToolExecutionAuthorization.Boundary.DISPATCH);
            Instant technical = started.plusSeconds(10).isBefore(deadline) ? started.plusSeconds(10) : deadline;
            return new WriteCheckVO(ticket, checked, executor, new ToolInvocationStore.DispatchWindowVO(started, nanos, technical, deadline));
        }
        WriteAttemptVO begin(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.RecorderPreparationVO prepared) {
            var check = check(request, prepared); String nonce = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            var result = store.beginIdempotentWriteDispatch(request, prepared, check.evidence(), check.checked(), check.executor(), ToolInvocationStore.dispatchNonceSha256(nonce),
                    new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, "ACTION_NOT_DISPATCHABLE", new BigDecimal("0.0100")), check.window());
            return new WriteAttemptVO(request, result, nonce);
        }
    }

    private static void readOnlyRecording(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                          ToolCapabilityStore capabilities) throws Exception {
        var fixture = new RecorderFixture(source, jdbc, ledger, capabilities);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var observed = new CountDownLatch(1);
        var calls = new AtomicInteger(); var start = new CountDownLatch(1);
        var transport = controlledTransport(request -> {
            calls.incrementAndGet(); entered.countDown();
            assertEquals("DISPATCHING", invocationState(jdbc, fixture.request));
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ReadOnlyDecisionToolTransport.Failure(ReadOnlyDecisionToolTransport.FailureKind.CANCELLED); }
            return observation();
        });
        var results = new ArrayList<ToolInvocationRecorder.RecordResult>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<ToolInvocationRecorder.RecordResult>>();
            for (int index = 0; index < 20; index++) futures.add(pool.submit(() -> {
                start.await(); var result = fixture.recorder.executeReadOnly(transport, fixture.request);
                if (!result.operationInvoked()) observed.countDown(); return result;
            }));
            start.countDown();
            try { assertTrue(entered.await(10, TimeUnit.SECONDS)); assertTrue(observed.await(10, TimeUnit.SECONDS)); }
            finally { release.countDown(); }
            for (var future : futures) { var result = future.get(20, TimeUnit.SECONDS); assertNotNull(result); results.add(result); }
        }
        assertEquals(1, calls.get()); assertEquals(1, results.stream().filter(ToolInvocationRecorder.RecordResult::operationInvoked).count());
        assertEquals("SUCCEEDED", invocationState(jdbc, fixture.request));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=?", Integer.class, ACTOR.tenantId(), fixture.run.runId()));
        var dispatch = jdbc.queryForMap("SELECT dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?",
                ACTOR.tenantId(), fixture.run.runId(), fixture.request.invocationId());
        assertEquals(1, ((Number) dispatch.get("dispatch_epoch")).intValue());
        assertTrue(((String) dispatch.get("dispatch_nonce_sha256")).matches("[0-9a-f]{64}"));
        assertEquals(EXECUTOR.issuerFingerprint(), dispatch.get("executor_issuer")); assertEquals(EXECUTOR.subjectId(), dispatch.get("executor_id"));
        assertEquals(0, ((Number) invocationCounters(jdbc, fixture.run.runId()).get("active_tool_calls")).intValue());
        assertEquals(1, ((Number) invocationCounters(jdbc, fixture.run.runId()).get("tool_calls_reserved")).intValue());
        recorderAccountingFailure(source, jdbc, ledger, capabilities);
        recorderCompletionRollback(source, jdbc, ledger, capabilities);
        recorderLateCompletion(source, jdbc, ledger, capabilities);
    }

    private static void recorderAccountingFailure(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                                  ToolCapabilityStore capabilities) {
        var fixture = new RecorderFixture(source, jdbc, ledger, capabilities); var calls = new AtomicInteger();
        var transport = controlledTransport(request -> { calls.incrementAndGet(); return observation(); });
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_accounting_test_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled accounting failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_accounting_test_fail BEFORE UPDATE ON tianji_agent.agent_budget_reservation FOR EACH ROW WHEN (OLD.state='RESERVED' AND NEW.state='SETTLED') EXECUTE FUNCTION tianji_agent.tool_accounting_test_fail()");
        try {
            var result = fixture.recorder.executeReadOnly(transport, fixture.request); assertNotNull(result);
            assertEquals("SUCCEEDED", result.state()); assertEquals(ToolInvocationRecorder.AccountingState.PENDING, result.accountingState());
            assertEquals("SUCCEEDED", invocationState(jdbc, fixture.request));
            assertEquals("RESERVED", jdbc.queryForObject("SELECT state FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?",
                    String.class, ACTOR.tenantId(), fixture.run.runId(), fixture.request.reservationId()));
        } finally {
            jdbc.execute("DROP TRIGGER tool_accounting_test_fail ON tianji_agent.agent_budget_reservation"); jdbc.execute("DROP FUNCTION tianji_agent.tool_accounting_test_fail()");
        }
        new ToolExecutionStorageVerifier(source).verify();
        var replay = fixture.recorder.executeReadOnly(transport, fixture.request); assertNotNull(replay);
        assertEquals("SUCCEEDED", replay.state()); assertFalse(replay.operationInvoked());
        assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, replay.accountingState()); assertEquals(1, calls.get());
    }

    private static void recorderCompletionRollback(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                                    ToolCapabilityStore capabilities) {
        var fixture = new RecorderFixture(source, jdbc, ledger, capabilities); var calls = new AtomicInteger();
        var transport = controlledTransport(request -> { calls.incrementAndGet(); return observation(); });
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_completion_test_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled completion failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_completion_test_fail BEFORE UPDATE ON tianji_agent.agent_tool_invocation FOR EACH ROW WHEN (OLD.state='DISPATCHING' AND NEW.state='SUCCEEDED') EXECUTE FUNCTION tianji_agent.tool_completion_test_fail()");
        try {
            var failure = assertThrows(AgentLedgerException.class, () -> fixture.recorder.executeReadOnly(transport, fixture.request));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause());
            assertEquals("DISPATCHING", invocationState(jdbc, fixture.request));
            assertEquals("RESERVED", jdbc.queryForObject("SELECT state FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?",
                    String.class, ACTOR.tenantId(), fixture.run.runId(), fixture.request.reservationId()));
            assertNull(jdbc.queryForObject("SELECT outcome::text FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?",
                    String.class, ACTOR.tenantId(), fixture.run.runId(), fixture.request.invocationId()));
        } finally {
            jdbc.execute("DROP TRIGGER tool_completion_test_fail ON tianji_agent.agent_tool_invocation"); jdbc.execute("DROP FUNCTION tianji_agent.tool_completion_test_fail()");
        }
        new ToolExecutionStorageVerifier(source).verify();
        var replay = fixture.recorder.executeReadOnly(transport, fixture.request); assertNotNull(replay);
        assertEquals("DISPATCHING", replay.state()); assertFalse(replay.operationInvoked()); assertEquals(1, calls.get());
    }

    private static void recorderLateCompletion(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                               ToolCapabilityStore capabilities) {
        var fixture = new RecorderFixture(source, jdbc, ledger, capabilities);
        var result = fixture.recorder.executeReadOnly(controlledTransport(request -> {
            var current = ledger.get(ACTOR, new GetAgentRunQuery(fixture.run.runId()));
            ledger.finish(ACTOR, fixture.lease, current.revision(), new AgentExecutionResult(fixture.run.runId(), AgentRunStatusEnum.COMPLETED,
                    Map.of(), null, null, null, null, "NONE"));
            // Simulate a later legal slot generation without using the old grant as completion authority.
            jdbc.update("UPDATE tianji_agent.agent_tool_capability SET issue_epoch=issue_epoch+1,grant_id=?,ciphertext='ENC:v1:test:B:B',updated_at=clock_timestamp() WHERE tenant_id=? AND run_id=?",
                    "9" + "2".repeat(62), ACTOR.tenantId(), fixture.run.runId());
            return observation();
        }), fixture.request);
        assertNotNull(result); assertEquals("SUCCEEDED", result.state());
        assertEquals(ToolInvocationRecorder.AccountingState.PENDING, result.accountingState());
        assertEquals("SUCCEEDED", invocationState(jdbc, fixture.request));
        assertEquals(AgentRunStatusEnum.COMPLETED, ledger.get(ACTOR, new GetAgentRunQuery(fixture.run.runId())).status());
    }

    private static ReadOnlyDecisionToolTransport controlledTransport(Function<ReadOnlyDecisionToolTransport.Request, ReadOnlyDecisionToolTransport.DecisionObservation> operation) {
        return new ReadOnlyDecisionToolTransport() {
            @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return callback.apply(new VerifiedExecutor(EXECUTOR, request.deadlineAt()), () -> operation.apply(request));
            }
        };
    }

    private static ReadOnlyDecisionToolTransport.DecisionObservation observation() {
        return new ReadOnlyDecisionToolTransport.DecisionObservation(ReadOnlyDecisionToolTransport.Status.MATCHED, UUID.randomUUID().toString(),
                Instant.now(), Instant.now(), Map.of("reference", "evaluated"), "row:1");
    }

    private static String invocationState(JdbcTemplate jdbc, ToolInvocationStore.PreparationRequestVO request) {
        return jdbc.queryForObject("SELECT state FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?",
                String.class, ACTOR.tenantId(), request.binding().run().runId(), request.invocationId());
    }

    private static final class RecorderFixture {
        final AgentRunAuthorization.Request run;
        final AgentRunLease lease;
        final ToolInvocationStore.PreparationRequestVO request;
        final ToolInvocationRecorder recorder;

        RecorderFixture(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger, ToolCapabilityStore capabilities) {
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(30), 5, 32, 10_000, 10_000,
                    null, null, 8, Set.of(READ_DESCRIPTOR.versionKey()), "no-retry", null);
            ledger.publish(ACTOR, new AgentDefinitionSpecDTO("tool-recorder-test", 1, "CODE_FIRST", "controlled-test", "text-v1", "text-v1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(READ_DESCRIPTOR.reference()), "model-route", "tool-policy-v1"), policy, Map.of()));
            var queued = ledger.start(ACTOR, new StartAgentRunCommand("tool-recorder-test", 1, null, UUID.randomUUID().toString(), null, null,
                    new AgentInputDTO("readonly recorder fixture", List.of(), Map.of()), null));
            run = ledger.executionMaterial(ACTOR, new GetAgentRunQuery(queued.runId())).authorization();
            var binding = new ToolExecutionGrant.BindingVO(run, READ_DESCRIPTOR.reference(), READ_DESCRIPTOR.policy().bindingSha256(), "TIANJI_TOOL_DMN_TEST_V1", RESOURCE, "tianji.api.agent.start", null);
            var reserved = capabilities.reserve(binding, READ_DESCRIPTOR).orElseThrow();
            var ticket = evidence(reserved, now(jdbc).plusSeconds(120).truncatedTo(ChronoUnit.MICROS)); assertTrue(capabilities.attach(ticket, READ_DESCRIPTOR));
            var claim = ledger.claim(ACTOR, new GetAgentRunQuery(run.runId()), ledger.get(ACTOR, new GetAgentRunQuery(run.runId())).revision(), "recorder-test", Duration.ofMinutes(5)); lease = claim.lease();
            var authority = new ToolExecutionAuthorization() {
                @Override public Decision authorize(Request value, Boundary boundary) { return check(value, boundary).decision(); }
                @Override public CheckResult check(Request value, Boundary boundary) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertEquals(run, value.run());
                    return new CheckResult(value, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary, Decision.ALLOW,
                            ACTOR.subjectType(), ticket.grantId(), 1L, null, 1L, Instant.now(), ticket.expiresAt()));
                }
            };
            var preparations = new ToolInvocationStore(source, ledger, capabilities, authority); preparations.verifyStorage();
            recorder = new ToolInvocationRecorder(source, ledger, preparations, capabilities, authority); recorder.verifyStorage();
            request = new ToolInvocationStore.PreparationRequestVO(binding, lease, READ_DESCRIPTOR, Map.of("amount", 20L), null, UUID.randomUUID(), BigDecimal.ZERO, null);
        }
    }

    private static void invocationPreparation(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                              ToolCapabilityStore capabilities) throws Exception {
        var run = start(ledger, ACTOR); var binding = binding(run);
        var reserved = capabilities.reserve(binding, DESCRIPTOR).orElseThrow();
        var ticket = evidence(reserved, now(jdbc).plusSeconds(120).truncatedTo(ChronoUnit.MICROS));
        assertTrue(capabilities.attach(ticket, DESCRIPTOR));
        var claim = ledger.claim(ACTOR, new GetAgentRunQuery(run.runId()), ledger.get(ACTOR, new GetAgentRunQuery(run.runId())).revision(),
                "invocation-test", Duration.ofMinutes(5));
        var authority = new ToolExecutionAuthorization() {
            @Override public Decision authorize(Request request, Boundary boundary) { return check(request, boundary).decision(); }
            @Override public CheckResult check(Request request, Boundary boundary) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(run, request.run()); assertEquals(Boundary.PREPARE, boundary);
                return new CheckResult(request, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary, Decision.ALLOW,
                        ACTOR.subjectType(), ticket.grantId(), 1L, null, 1L, Instant.now(), ticket.expiresAt()));
            }
        };
        var invocations = new ToolInvocationStore(source, ledger, capabilities, authority); invocations.verifyStorage();
        var action = new ToolActionRequest(ACTOR, DESCRIPTOR, "invocation-" + UUID.randomUUID(), "1", Map.of("amount", 20L));
        var id = UUID.randomUUID(); var expiry = now(jdbc).plusSeconds(600); var cost = new BigDecimal("0.20");
        var request = new ToolInvocationStore.PreparationRequestVO(binding, claim.lease(), DESCRIPTOR, action.arguments(), action, id, cost, expiry);
        var gate = new CountDownLatch(1);
        var results = new ArrayList<ToolInvocationStore.PreparationResult>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<ToolInvocationStore.PreparationResult>>();
            for (int index = 0; index < 20; index++) futures.add(executor.submit(() -> { gate.await(); return invocations.prepare(request); }));
            gate.countDown();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
        }
        var first = results.getFirst(); assertNotNull(first); assertNotNull(first.invocation());
        for (var result : results) {
            assertNotNull(result);
            var invocation = result.invocation(); assertNotNull(invocation);
            assertEquals(run.runId(), invocation.runId()); assertEquals(id, invocation.invocationId());
            assertEquals("tool:" + id, invocation.reservationId()); assertEquals("PREPARED", invocation.state());
            assertEquals(1, invocation.revision()); assertEquals(reserved.issueEpoch(), invocation.capabilityIssueEpoch());
            assertEquals(first.invocation().createdAt(), invocation.createdAt());
            assertEquals(first.invocation().dispatchNotAfter(), invocation.dispatchNotAfter()); assertNull(invocation.finishedAt());
            assertEquals(first.action(), result.action());
        }
        assertEquals(1, results.stream().filter(result -> result.invocation().newlyCreated()).count());
        assertEquals(first.invocation().createdAt().plusSeconds(10), first.invocation().dispatchNotAfter());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=?", Integer.class, ACTOR.tenantId(), run.runId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=?", Integer.class, ACTOR.tenantId(), run.runId()));
        var changed = new ToolActionRequest(ACTOR, DESCRIPTOR, action.businessKey(), action.businessVersion(), Map.of("amount", 21L));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> invocations.prepare(
                new ToolInvocationStore.PreparationRequestVO(binding, claim.lease(), DESCRIPTOR, changed.arguments(), changed, id, cost, expiry))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> invocations.prepare(
                new ToolInvocationStore.PreparationRequestVO(binding, claim.lease(), DESCRIPTOR, action.arguments(), action, id, new BigDecimal("0.21"), expiry))).code());
        var existing = invocations.prepare(new ToolInvocationStore.PreparationRequestVO(binding, claim.lease(), DESCRIPTOR, action.arguments(), action,
                UUID.randomUUID(), cost, expiry.plusSeconds(60)));
        assertNull(existing.invocation()); assertEquals(first.action(), existing.action());
        Map<String, Object> counters = invocationCounters(jdbc, run.runId());
        assertEquals(1, ((Number) counters.get("tool_calls_reserved")).intValue());
        assertEquals(1, ((Number) counters.get("active_tool_calls")).intValue());
        assertEquals(0, ((BigDecimal) counters.get("estimated_cost_reserved")).compareTo(cost));
        var failedAction = new ToolActionRequest(ACTOR, DESCRIPTOR, "invocation-rollback-" + UUID.randomUUID(), "1", action.arguments());
        var failedId = UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_invocation_test_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled invocation insert failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_invocation_test_fail BEFORE INSERT ON tianji_agent.agent_tool_invocation FOR EACH ROW EXECUTE FUNCTION tianji_agent.tool_invocation_test_fail()");
        try {
            var failure = assertThrows(AgentLedgerException.class, () -> invocations.prepare(new ToolInvocationStore.PreparationRequestVO(binding,
                    claim.lease(), DESCRIPTOR, failedAction.arguments(), failedAction, failedId, cost, expiry)));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause());
            assertEquals(0, count(jdbc, failedAction)); assertEquals(counters, invocationCounters(jdbc, run.runId()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?", Integer.class, ACTOR.tenantId(), run.runId(), failedId));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?", Integer.class, ACTOR.tenantId(), run.runId(), "tool:" + failedId));
        } finally {
            jdbc.execute("DROP TRIGGER tool_invocation_test_fail ON tianji_agent.agent_tool_invocation");
            jdbc.execute("DROP FUNCTION tianji_agent.tool_invocation_test_fail()");
        }
        new ToolExecutionStorageVerifier(source).verify();
    }

    private static Map<String, Object> invocationCounters(JdbcTemplate jdbc, String runId) {
        return jdbc.queryForMap("SELECT tool_calls_reserved,active_tool_calls,estimated_cost_reserved FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?", ACTOR.tenantId(), runId);
    }

    private static void capabilityEpochs(JdbcTemplate jdbc, ToolCapabilityStore capabilities, ToolExecutionGrant.BindingVO binding) {
        var first = capabilities.reserve(binding, DESCRIPTOR).orElseThrow(); var second = capabilities.reserve(binding, DESCRIPTOR).orElseThrow();
        assertEquals(first.issueEpoch() + 1, second.issueEpoch());
        Instant expiry = now(jdbc).plusSeconds(120).plusNanos(123);
        assertFalse(capabilities.attach(evidence(first, expiry), DESCRIPTOR)); assertTrue(capabilities.attach(evidence(second, expiry), DESCRIPTOR));
        assertEquals(expiry.truncatedTo(ChronoUnit.MICROS), capabilities.current(binding, DESCRIPTOR).orElseThrow().expiresAt());
        assertTrue(capabilities.reserve(binding, DESCRIPTOR).isEmpty());
        var changed = new ToolExecutionGrant.BindingVO(binding.run(), binding.tool(), binding.backendBindingSha256(), binding.purpose(),
                URI.create("https://AGENT.example/api"), binding.authorizationResourceKey(), null);
        assertEquals(binding.capabilityId(), changed.capabilityId());
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> capabilities.reserve(changed, DESCRIPTOR)).code());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tianji_agent.agent_tool_capability SET grant_id=? WHERE tenant_id=? AND run_id=?", "8" + "1".repeat(62), ACTOR.tenantId(), binding.run().runId()));
    }

    private static void replayConflictAndScope(AgentLedgerService ledger, ToolActionIntentStore intents, AgentRunAuthorization.Request run, AgentRunLease lease,
                                               ToolActionRequest action, ToolActionIntentStore.PreparedActionResult prepared) {
        var changed = new ToolActionRequest(ACTOR, DESCRIPTOR, action.businessKey(), action.businessVersion(), Map.of("amount", 21L));
        assertEquals(action.intentId(), changed.intentId());
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> intents.prepare(run, lease, changed, DESCRIPTOR, prepared.expiresAt())).code());
        var anotherRun = start(ledger, ACTOR); var anotherClaim = ledger.claim(ACTOR, new GetAgentRunQuery(anotherRun.runId()),
                ledger.get(ACTOR, new GetAgentRunQuery(anotherRun.runId())).revision(), "second-tool-test", Duration.ofMinutes(5));
        assertEquals(prepared, intents.prepare(anotherRun, anotherClaim.lease(), action, DESCRIPTOR, prepared.expiresAt().plusSeconds(1)));
        assertEquals(run.runId(), prepared.originRunId());
        var foreignActor = TrustedAgentActor.verified(ACTOR.tenantId(), ACTOR.subjectType(), ACTOR.subjectId(), "https://other.example/20");
        var foreignRun = start(ledger, foreignActor); var foreignClaim = ledger.claim(foreignActor, new GetAgentRunQuery(foreignRun.runId()),
                ledger.get(foreignActor, new GetAgentRunQuery(foreignRun.runId())).revision(), "foreign-tool-test", Duration.ofMinutes(5));
        assertThrows(IllegalArgumentException.class, () -> intents.current(foreignRun, foreignClaim.lease(), action, DESCRIPTOR));
        var foreignAction = new ToolActionRequest(foreignActor, DESCRIPTOR, action.businessKey(), action.businessVersion(), action.arguments());
        assertNotEquals(action.intentId(), foreignAction.intentId()); assertTrue(intents.current(foreignRun, foreignClaim.lease(), foreignAction, DESCRIPTOR).isEmpty());
    }

    private static void concurrentPreparation(JdbcTemplate jdbc, ToolActionIntentStore intents, AgentRunAuthorization.Request run, AgentRunLease lease) throws Exception {
        var action = new ToolActionRequest(ACTOR, DESCRIPTOR, "concurrent-" + UUID.randomUUID(), "1", Map.of("amount", 20L));
        Instant expiry = now(jdbc).plusSeconds(600); var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<ToolActionIntentStore.PreparedActionResult>>();
            for (int index = 0; index < 20; index++) futures.add(executor.submit(() -> { gate.await(); return intents.prepare(run, lease, action, DESCRIPTOR, expiry); }));
            gate.countDown(); var first = futures.getFirst().get(20, TimeUnit.SECONDS);
            for (var future : futures) assertEquals(first, future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, count(jdbc, action));
    }

    private static void rollback(DriverManagerDataSource source, JdbcTemplate jdbc, ToolActionIntentStore intents, AgentRunAuthorization.Request run, AgentRunLease lease) {
        var action = new ToolActionRequest(ACTOR, DESCRIPTOR, "rollback-" + UUID.randomUUID(), "1", Map.of("amount", 20L));
        jdbc.execute("CREATE FUNCTION tianji_agent.tool_journal_test_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled tool journal insert failure'; END; $$");
        jdbc.execute("CREATE TRIGGER tool_journal_test_fail BEFORE INSERT ON tianji_agent.agent_tool_action_intent FOR EACH ROW EXECUTE FUNCTION tianji_agent.tool_journal_test_fail()");
        try {
            var failure = assertThrows(AgentLedgerException.class, () -> intents.prepare(run, lease, action, DESCRIPTOR, now(jdbc).plusSeconds(600)));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause());
            assertEquals(0, count(jdbc, action));
        } finally {
            jdbc.execute("DROP TRIGGER tool_journal_test_fail ON tianji_agent.agent_tool_action_intent"); jdbc.execute("DROP FUNCTION tianji_agent.tool_journal_test_fail()");
        }
        new ToolExecutionStorageVerifier(source).verify();
        assertNotNull(intents.prepare(run, lease, action, DESCRIPTOR, now(jdbc).plusSeconds(600)));
    }

    private static void catalogDrift(DriverManagerDataSource source, JdbcTemplate jdbc) {
        var verifier = new ToolExecutionStorageVerifier(source);
        String original = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='tianji_agent.agent_tool_capability'::regclass AND conname='tool_capability_ticket'", String.class);
        jdbc.execute("ALTER TABLE tianji_agent.agent_tool_capability DROP CONSTRAINT tool_capability_ticket");
        jdbc.execute("ALTER TABLE tianji_agent.agent_tool_capability ADD CONSTRAINT tool_capability_ticket CHECK (true)");
        try { assertThrows(AgentLedgerException.class, verifier::verify); }
        finally {
            jdbc.execute("ALTER TABLE tianji_agent.agent_tool_capability DROP CONSTRAINT tool_capability_ticket");
            jdbc.execute("ALTER TABLE tianji_agent.agent_tool_capability ADD CONSTRAINT tool_capability_ticket " + original);
        }
        jdbc.execute("ALTER TABLE tianji_agent.agent_tool_action_intent ALTER COLUMN dispatch_epoch SET DEFAULT 1");
        try { assertThrows(AgentLedgerException.class, verifier::verify); }
        finally { jdbc.execute("ALTER TABLE tianji_agent.agent_tool_action_intent ALTER COLUMN dispatch_epoch SET DEFAULT 0"); }
        String index = jdbc.queryForObject("SELECT pg_get_indexdef('tianji_agent.agent_tool_action_reconcile_idx'::regclass)", String.class);
        jdbc.execute("DROP INDEX tianji_agent.agent_tool_action_reconcile_idx");
        jdbc.execute("CREATE INDEX agent_tool_action_reconcile_idx ON tianji_agent.agent_tool_action_intent (tenant_id,state,updated_at,intent_id) WHERE state='UNKNOWN'");
        try { assertThrows(AgentLedgerException.class, verifier::verify); }
        finally { jdbc.execute("DROP INDEX tianji_agent.agent_tool_action_reconcile_idx"); jdbc.execute(index); }
        jdbc.execute("ALTER TABLE tianji_agent.agent_tool_invocation ENABLE ROW LEVEL SECURITY");
        try { assertThrows(AgentLedgerException.class, verifier::verify); }
        finally { jdbc.execute("ALTER TABLE tianji_agent.agent_tool_invocation DISABLE ROW LEVEL SECURITY"); }
        verifier.verify();
    }

    private static AgentRunAuthorization.Request start(AgentLedgerService ledger, TrustedAgentActor actor) {
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(10), 5, 32, 10_000, 10_000,
                null, null, 8, Set.of(DESCRIPTOR.versionKey()), "no-retry", null);
        ledger.publish(actor, new AgentDefinitionSpecDTO("tool-journal-test", 1, "CODE_FIRST", "controlled-test", "text-v1", "text-v1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(DESCRIPTOR.reference()), "model-route", "tool-policy-v1"), policy, Map.of()));
        var run = ledger.start(actor, new StartAgentRunCommand("tool-journal-test", 1, null, UUID.randomUUID().toString(), null, null,
                new AgentInputDTO("bounded journal fixture", List.of(), Map.of()), null));
        return ledger.executionMaterial(actor, new GetAgentRunQuery(run.runId())).authorization();
    }
    private static ToolExecutionGrant.BindingVO binding(AgentRunAuthorization.Request run) {
        return new ToolExecutionGrant.BindingVO(run, DESCRIPTOR.reference(), DESCRIPTOR.policy().bindingSha256(), "TIANJI_TOOL_PROCESS_START_V1", RESOURCE, "tianji.api.agent.start", null);
    }
    private static ToolExecutionGrant.EvidenceVO evidence(ToolExecutionGrant.ReservationVO reservation, Instant expiry) {
        return new ToolExecutionGrant.EvidenceVO(reservation, "9" + "1".repeat(62), "ENC:v1:test:A:A", expiry);
    }
    private static int count(JdbcTemplate jdbc, ToolActionRequest action) { return jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?", Integer.class, action.owner().tenantId(), UUID.fromString(action.intentId())); }
    private static Instant now(JdbcTemplate jdbc) { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
    private static Flyway flyway(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent").locations("classpath:db/tianji-agent")
                .placeholders(Map.of("tianjiMemoryDimensions", "2")).target(target).cleanDisabled(true).load();
    }
}
