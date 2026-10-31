package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
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
