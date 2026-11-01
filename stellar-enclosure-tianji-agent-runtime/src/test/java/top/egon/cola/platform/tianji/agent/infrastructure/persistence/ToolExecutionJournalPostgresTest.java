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
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
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
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.net.URI;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Requires an explicit empty loopback database and pinned preinstalled vector; authority is controlled, not live IAM. */
@EnabledIfSystemProperty(named = "tianji.tool.journal.postgres.test.url", matches = ".+")
@Timeout(120)
class ToolExecutionJournalPostgresTest {
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
        String url = System.getProperty("tianji.tool.journal.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) throw new IllegalArgumentException("Tool journal tests require an explicit empty loopback tianji_it_ database");
        String vectorVersion = System.getProperty("tianji.tool.journal.postgres.vector.version");
        if (vectorVersion == null || !vectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Tool journal tests require an explicitly pinned preinstalled vector patch");
        var source = new DriverManagerDataSource(url); var properties = new Properties();
        properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "15"); source.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        assertEquals(vectorVersion, jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
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
