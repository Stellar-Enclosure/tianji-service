package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.RuntimeBindingBO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.RuntimeCompatibilityIT.HASH;

class RuntimeSafetyReceiptIT {
    private static final Runnable PERMITTED = () -> { };
    private static final ToolExecutionJsonCodec TOOL_CODEC = new ToolExecutionJsonCodec();
    private static final ToolSchema EMPTY_TOOL_SCHEMA = new ToolSchema(Map.of(), Set.of());
    private static final ToolDescriptor WRITE_TOOL = new ToolDescriptor("tool", 1, "write_tool", "Bounded fixture",
            EMPTY_TOOL_SCHEMA, EMPTY_TOOL_SCHEMA,
            new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "action", "tool.start", HASH, null));
    private static final String CANONICAL_SUCCESS = TOOL_CODEC.outcome(ToolExecutionJsonCodec.ActionProfile.RUNTIME_V2, WRITE_TOOL,
            new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of(), "effect", null, null));
    private static RuntimeCompatibilityIT.Fixture database;
    private static AgentRuntimeBindingStore bindings;
    private static AgentRuntimeCallReceiptStore calls;
    private static TransactionTemplate transaction;
    private static JdbcTemplate runtime;

    @BeforeAll
    static void migrateOnce() {
        database = RuntimeCompatibilityIT.provisionV16("receipt");
        database.installV17();
        var source = database.runtime();
        bindings = new AgentRuntimeBindingStore(source);
        calls = new AgentRuntimeCallReceiptStore(source, bindings);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setTimeout(10);
        runtime = new JdbcTemplate(source);
    }

    @Test
    void replayChargesOnceAndDispatchAndTerminalTransitionsUseRevisionFences() {
        var run = seed();
        var request = request(run, UUID.randomUUID());
        var first = prepare(run, request);
        assertEquals(RuntimeCallReceiptBO.State.PREPARED, first.getState());
        assertEquals(first, prepare(run, request));
        assertThrows(AgentLedgerException.class, () -> prepare(run, request(run, request.getCallId()).setInputSha256("b".repeat(64))));
        assertEquals(List.of(1, 0, 1), counters(run));
        var concurrent = request(run, UUID.randomUUID());
        assertEquals("TIANJI_CAPACITY_EXCEEDED", assertThrows(AgentLedgerException.class, () -> prepare(run, concurrent)).code().name());

        var dispatched = inTransaction(() -> calls.markDispatched(run.owner(), run.lease(), 1, request.getCallId(),
                first.getRevision(), null, PERMITTED));
        assertEquals(RuntimeCallReceiptBO.State.DISPATCHED, dispatched.getState());
        assertNotNull(dispatched.getDispatchedAt());
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.markDispatched(run.owner(), run.lease(), 1,
                request.getCallId(), first.getRevision(), null, PERMITTED)));

        var outcome = new RuntimeCallReceiptBO.OutcomeVO(RuntimeCallReceiptBO.State.SUCCEEDED, HASH, 7L, null, null);
        var terminal = inTransaction(() -> calls.finish(run.owner(), run.lease(), 1, request.getCallId(),
                dispatched.getRevision(), null, outcome, PERMITTED));
        assertEquals(7L, terminal.getInputTokens());
        assertNull(terminal.getOutputTokens());
        assertNotNull(terminal.getFinishedAt());
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.finish(run.owner(), run.lease(), 1,
                request.getCallId(), dispatched.getRevision(), null, outcome, PERMITTED)));
        assertEquals(List.of(1, 0, 0), counters(run));
    }

    @Test
    void rejectsStaleLeaseOwnerRevisionAndDatabaseDeadlineEquality() {
        var run = seed();
        var request = request(run, UUID.randomUUID());
        var outdated = new AgentRunLease(run.lease().runId(), run.lease().owner(), 2, run.lease().expiresAt());
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.prepare(run.owner(), outdated, 1, request, PERMITTED)));
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.prepare(RuntimeCompatibilityIT.owner(),
                run.lease(), 1, request, PERMITTED)));
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.prepare(run.owner(), run.lease(), 2, request, PERMITTED)));
        assertThrows(IllegalStateException.class, () -> inTransaction(() -> calls.prepare(run.owner(), run.lease(), 1, request,
                () -> { throw new IllegalStateException("Host revoked current permission"); })));
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.prepare(run.owner(), run.lease(), 1,
                request.setDeadlineAt(now(runtime)), PERMITTED)));
        assertEquals(List.of(0, 0, 0), counters(run));
    }

    @Test
    void concurrentReplayAndForcedRollbackKeepReceiptAndCountersAtomic() throws Exception {
        var run = seed();
        UUID rolledBack = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            calls.prepare(run.owner(), run.lease(), 1, request(run, rolledBack), PERMITTED);
            throw new IllegalStateException("Simulated surrounding transaction failure");
        }));
        assertEquals(List.of(0, 0, 0), counters(run));
        assertEquals(0, runtime.queryForObject("""
                SELECT count(*) FROM tianji_agent.agent_runtime_call_receipt WHERE call_id=?
                """, Integer.class, rolledBack));

        UUID call = UUID.randomUUID();
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { barrier.await(); return prepare(run, request(run, call)); });
            var second = workers.submit(() -> { barrier.await(); return prepare(run, request(run, call)); });
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(List.of(1, 0, 1), counters(run));
    }

    @Test
    void rejectsMixedProfileMissingBindingAndAcceptsLiveDatabaseLeaseOverStaleHint() {
        var owner = RuntimeCompatibilityIT.owner();
        var old = UUID.randomUUID().toString();
        RuntimeCompatibilityIT.insertRun(database.admin(), owner, old, "{}");
        var newRun = UUID.randomUUID().toString();
        RuntimeCompatibilityIT.insertRun(database.admin(), owner, newRun, runtimeSnapshot());
        var deadline = now(runtime).plusSeconds(120);
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> bindings.create(owner, binding(owner, old, deadline))));
        var lease = new AgentRunLease(newRun, "worker", 1, now(runtime).minusSeconds(1));
        var run = new Run(owner, lease, deadline);
        var request = request(run, UUID.randomUUID());
        assertThrows(AgentLedgerException.class, () -> prepare(run, request));
        inTransaction(() -> bindings.create(owner, binding(owner, newRun, deadline)));
        assertEquals(RuntimeCallReceiptBO.State.PREPARED, prepare(run, request).getState());
    }

    @Test
    void dispatchCommitHasExactBusinessFenceAndRecoveryDoesNotReleaseAgain() {
        var run = seed();
        var request = seedWrite(run);
        var prepared = prepare(run, request);
        var call = request.getCallId();
        var dispatch = new RuntimeCallReceiptBO.DispatchVO(1, HASH, HASH, "executor");
        assertThrows(UnexpectedRollbackException.class, () -> inTransaction(() -> {
            assertThrows(IllegalStateException.class, () -> calls.markDispatched(run.owner(), run.lease(), 1, call,
                    prepared.getRevision(), dispatch, PERMITTED, receipt -> {
                        recordBusinessDispatch(receipt);
                        throw new IllegalStateException("Simulated action writer failure");
                    }));
            return null;
        }));
        assertEquals(prepared, prepare(run, request));
        assertNull(runtime.queryForObject("""
                SELECT dispatch_call_id FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?
                """, UUID.class, run.owner().tenantId(), request.getIntentId()));

        var sent = inTransaction(() -> calls.markDispatched(run.owner(), run.lease(), 1, call,
                prepared.getRevision(), dispatch, PERMITTED, RuntimeSafetyReceiptIT::recordBusinessDispatch));
        assertEquals(request.getCallId(), runtime.queryForObject("""
                SELECT dispatch_call_id FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?
                """, UUID.class, run.owner().tenantId(), request.getIntentId()));
        var wrong = new RuntimeCallReceiptBO.DispatchVO(1, "f".repeat(64), HASH, "executor");
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.finish(run.owner(), run.lease(), 1, call,
                sent.getRevision(), wrong, new RuntimeCallReceiptBO.OutcomeVO(RuntimeCallReceiptBO.State.SUCCEEDED, HASH, null, null, null), PERMITTED)));
        database.admin().update("UPDATE tianji_agent.agent_run SET lease_epoch=lease_epoch+1 WHERE tenant_id=? AND run_id=?",
                run.owner().tenantId(), run.lease().runId());
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.recover(run.owner(), run.lease().runId(), call, sent.getRevision())));
        assertEquals(List.of(0, 1, 1), counters(run));
        var unknown = inTransaction(() -> calls.recover(run.owner(), run.lease().runId(), call, sent.getRevision(), receipt ->
                assertEquals(1, runtime.update("""
                    UPDATE tianji_agent.agent_tool_action_intent
                    SET state='UNKNOWN',revision=revision+1,side_effect_outcome='UNKNOWN',last_error_code='RUNTIME_OUTCOME_UNKNOWN',
                        result='{"status":"UNKNOWN","output":{},"businessReceiptId":null,"errorCode":"RUNTIME_OUTCOME_UNKNOWN"}',
                        updated_at=statement_timestamp(),closed_at=statement_timestamp()
                    WHERE tenant_id=? AND intent_id=? AND state='DISPATCHING' AND dispatch_nonce_sha256=?
                    """, receipt.getTenantId(), receipt.getIntentId(), receipt.getDispatch().nonceSha256()))));
        assertEquals(RuntimeCallReceiptBO.State.UNKNOWN, unknown.getState());
        assertEquals(List.of(0, 1, 0), counters(run));
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.recover(run.owner(), run.lease().runId(), call, sent.getRevision())));

        var wrongDigest = new RuntimeCallReceiptBO.OutcomeVO(RuntimeCallReceiptBO.State.SUCCEEDED, HASH, null, null, null);
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.reconcile(run.owner(), run.lease().runId(),
                call, unknown.getRevision(), wrong, wrongDigest, () -> { throw new AssertionError("Wrong nonce must not reach authority"); })));
        assertThrows(AgentLedgerException.class, () -> inTransaction(() -> calls.reconcile(run.owner(), run.lease().runId(),
                call, unknown.getRevision(), dispatch, wrongDigest, () -> recordBusinessSuccess(request))));
        assertEquals("UNKNOWN", runtime.queryForObject("""
                SELECT state FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?
                """, String.class, run.owner().tenantId(), request.getIntentId()));
        String expectedResult = OrchestrationRequestDigests.hash(List.of("TIANJI_RUNTIME_TOOL_RESULT_V2",
                request.getIntentId().toString(), HASH, CANONICAL_SUCCESS), Map.of());
        var outcome = new RuntimeCallReceiptBO.OutcomeVO(RuntimeCallReceiptBO.State.SUCCEEDED, expectedResult, null, null, null);
        var terminal = inTransaction(() -> calls.reconcile(run.owner(), run.lease().runId(), call, unknown.getRevision(),
                dispatch, outcome, () -> recordBusinessSuccess(request)));
        assertEquals(RuntimeCallReceiptBO.State.SUCCEEDED, terminal.getState());
        assertEquals(List.of(0, 1, 0), counters(run));
    }

    @Test
    void prepareUsesValidatedCopyAcrossPermissionCallbackAndRejectsBodies() {
        var run = seed();
        var call = UUID.randomUUID();
        var metadata = new LinkedHashMap<String, Object>(Map.of("modelRef", "registered-model-1", "sources", List.of(
                Map.of("sourceRef", "first", "sourceRevision", 2L, "contentSha256", HASH),
                Map.of("sourceRef", "second", "sourceRevision", 1L, "contentSha256", HASH))));
        var request = request(run, call).setContextMetadata(metadata);
        var prepared = inTransaction(() -> calls.prepare(run.owner(), run.lease(), 1, request, () -> {
            request.setDeadlineAt(run.deadline().plusSeconds(3600)).setCallId(UUID.randomUUID())
                    .setInputSha256("f".repeat(64)).setKind(RuntimeCallReceiptBO.Kind.TOOL);
            metadata.put("modelRef", "replacement");
        }));
        assertEquals(call, prepared.getCallId());
        assertEquals(run.deadline(), prepared.getDeadlineAt());
        assertEquals(HASH, prepared.getInputSha256());
        assertEquals(RuntimeCallReceiptBO.Kind.MODEL, prepared.getKind());
        assertEquals("registered-model-1", prepared.getContextMetadata().get("modelRef"));
        var sources = (List<?>) prepared.getContextMetadata().get("sources");
        assertEquals("first", ((Map<?, ?>) sources.getFirst()).get("sourceRef"));
        assertThrows(IllegalArgumentException.class, () -> prepare(run, request(run, UUID.randomUUID())
                .setContextMetadata(Map.of("modelRef", "registered-model-1", "prompt", "body"))));
        assertThrows(IllegalArgumentException.class, () -> prepare(run, request(run, UUID.randomUUID())
                .setContextMetadata(Map.of("modelRef", "registered-model-1", "sources", List.of(
                        Map.of("sourceRef", "first", "sourceRevision", 1, "contentSha256", HASH, "token", "forbidden"))))));
        assertEquals(List.of(1, 0, 1), counters(run));
    }

    @Test
    void dispatchRechecksDatabaseDeadlineAfterAtomicActionCallback() {
        var run = seed();
        var request = seedWrite(run).setDeadlineAt(now(runtime).plusSeconds(2));
        var prepared = prepare(run, request);
        var dispatch = new RuntimeCallReceiptBO.DispatchVO(1, HASH, HASH, "executor");
        assertEquals("TIANJI_DISPATCH_EXPIRED", assertThrows(AgentLedgerException.class, () -> inTransaction(() ->
                calls.markDispatched(run.owner(), run.lease(), 1, request.getCallId(), prepared.getRevision(), dispatch, PERMITTED, receipt -> {
                    recordBusinessDispatch(receipt);
                    runtime.query("SELECT pg_sleep_until(?)", rs -> { }, receipt.getDeadlineAt().atOffset(java.time.ZoneOffset.UTC));
                }))).code().name());
        assertEquals("PREPARED", runtime.queryForObject("""
                SELECT state FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=? AND call_id=?
                """, String.class, run.owner().tenantId(), run.lease().runId(), request.getCallId()));
        assertNull(runtime.queryForObject("""
                SELECT dispatch_call_id FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?
                """, UUID.class, run.owner().tenantId(), request.getIntentId()));
        assertEquals(List.of(0, 1, 1), counters(run));
    }

    private static void recordBusinessSuccess(RuntimeCallReceiptBO receipt) {
        assertEquals(1, runtime.update("""
                UPDATE tianji_agent.agent_tool_action_intent
                SET state='APPLIED',revision=revision+1,side_effect_outcome='CONFIRMED',business_receipt_id='effect',
                    result=?::jsonb,last_error_code=NULL,updated_at=statement_timestamp(),closed_at=statement_timestamp()
                WHERE tenant_id=? AND intent_id=? AND state='UNKNOWN' AND dispatch_nonce_sha256=?
                """, CANONICAL_SUCCESS, receipt.getTenantId(), receipt.getIntentId(), HASH));
    }

    private static void recordBusinessDispatch(RuntimeCallReceiptBO receipt) {
        var dispatch = receipt.getDispatch();
        assertEquals(1, runtime.update("""
                UPDATE tianji_agent.agent_tool_action_intent
                SET state='DISPATCHING',revision=revision+1,dispatch_epoch=?,dispatch_nonce_sha256=?,dispatch_run_id=?,
                    dispatch_call_id=?,executor_issuer=?,executor_id=?,side_effect_outcome='UNKNOWN',updated_at=statement_timestamp()
                WHERE tenant_id=? AND intent_id=? AND state='PREPARED' AND revision=1 AND action_profile='RUNTIME_V2'
                """, dispatch.epoch(), dispatch.nonceSha256(), receipt.getRunId(), receipt.getCallId(), dispatch.executorIssuer(),
                dispatch.executorId(), receipt.getTenantId(), receipt.getIntentId()));
    }

    private static RuntimeCallReceiptBO seedWrite(Run run) {
        var capability = UUID.randomUUID();
        var intent = UUID.randomUUID();
        var owner = run.owner();
        String toolSha256 = WRITE_TOOL.reference().contentSha256();
        database.admin().update("""
                INSERT INTO tianji_agent.agent_tool_capability
                  (tenant_id,run_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,backend_binding_sha256,
                   purpose,resource_uri,authorization_resource_key,capability_id,binding_sha256,issue_epoch,created_at,
                   updated_at,deadline_at)
                VALUES (?,?,'USER',?,?,'tool',1,?,?,'TIANJI_TOOL_START','https://tools.example/start','tool.start',?,?,1,
                    statement_timestamp(),statement_timestamp(),statement_timestamp()+interval '5 minutes')
                """, owner.tenantId(), run.lease().runId(), owner.issuerFingerprint(), owner.subjectId(), toolSha256, HASH, capability, HASH);
        database.admin().update("""
                INSERT INTO tianji_agent.agent_tool_action_intent
                  (tenant_id,intent_id,logical_identity_sha256,owner_type,owner_issuer,owner_id,action_key,business_key,
                   business_version,tool_key,tool_version,tool_sha256,binding_sha256,request_sha256,descriptor,arguments,
                   origin_run_id,origin_snapshot_sha256,risk,state,revision,side_effect_outcome,created_at,updated_at,expires_at,action_profile)
                VALUES (?,?,?,'USER',?,?,'action','business','v1','tool',1,?,?,?,?::jsonb,'{}',?,?,
                   'IDEMPOTENT_WRITE','PREPARED',1,'NONE',statement_timestamp(),statement_timestamp(),
                   statement_timestamp()+interval '1 hour','RUNTIME_V2')
                """, owner.tenantId(), intent, OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_ACTION_LOGICAL_IDENTITY_V1",
                        owner.identityFingerprint(), "action", "business", "v1"), Map.of()),
                owner.issuerFingerprint(), owner.subjectId(), toolSha256, HASH, HASH, TOOL_CODEC.descriptor(WRITE_TOOL), run.lease().runId(), HASH);
        return request(run, UUID.randomUUID()).setKind(RuntimeCallReceiptBO.Kind.TOOL)
                .setCapability(new RuntimeCallReceiptBO.CapabilityVO(capability, HASH, 1, null, "tool", 1, toolSha256, HASH,
                        ToolPolicy.Risk.IDEMPOTENT_WRITE)).setIntentId(intent).setContextMetadata(Map.of());
    }

    private static RuntimeCallReceiptBO prepare(Run run, RuntimeCallReceiptBO request) {
        return inTransaction(() -> calls.prepare(run.owner(), run.lease(), 1, request, PERMITTED));
    }

    private static Run seed() {
        var actor = RuntimeCompatibilityIT.owner();
        var runId = UUID.randomUUID().toString();
        RuntimeCompatibilityIT.insertRun(database.admin(), actor, runId, runtimeSnapshot());
        var deadline = now(runtime).plusSeconds(120);
        inTransaction(() -> bindings.create(actor, binding(actor, runId, deadline)));
        return new Run(actor, new AgentRunLease(runId, "worker", 1, deadline), deadline);
    }

    private static String runtimeSnapshot() {
        return "{\"runtimeProfile\":\"RUNTIME_V2\",\"snapshotSha256\":\"" + HASH + "\"}";
    }

    private static RuntimeBindingBO binding(TrustedAgentActor actor, String runId, Instant deadline) {
        var limits = new TechnicalLimitsVO(Duration.ofMinutes(3), Duration.ofSeconds(10), Duration.ofSeconds(10),
                2, 2, 1, 4096, 1024);
        return new RuntimeBindingBO().setTenantId(actor.tenantId()).setRunId(runId).setProfile("RUNTIME_V2")
                .setSnapshotSha256(HASH).setConfigurationSha256(HASH).setTemplateSha256(HASH)
                .setPolicy(limits).setNodeExecutionRef("node-attempt-1").setAttemptDeadlineAt(deadline);
    }

    private static RuntimeCallReceiptBO request(Run run, UUID call) {
        return new RuntimeCallReceiptBO().setTenantId(run.owner().tenantId()).setRunId(run.lease().runId())
                .setCallId(call).setKind(RuntimeCallReceiptBO.Kind.MODEL).setOwner(run.owner()).setLeaseEpoch(1)
                .setInputSha256(HASH).setContextMetadata(Map.of("modelRef", "registered-model-1")).setDeadlineAt(run.deadline());
    }

    private static List<Integer> counters(Run run) {
        return runtime.queryForObject("""
                SELECT model_calls,tool_calls,active_calls FROM tianji_agent.agent_runtime_binding WHERE tenant_id=? AND run_id=?
                """, (rs, n) -> List.of(rs.getInt(1), rs.getInt(2), rs.getInt(3)), run.owner().tenantId(), run.lease().runId());
    }

    private static Instant now(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT clock_timestamp()", (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant());
    }

    private static <T> T inTransaction(java.util.function.Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    private record Run(TrustedAgentActor owner, AgentRunLease lease, Instant deadline) { }
}
