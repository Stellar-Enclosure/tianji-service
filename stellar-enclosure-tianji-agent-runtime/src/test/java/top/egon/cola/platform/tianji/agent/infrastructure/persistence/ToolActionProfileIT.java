package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.agentflow.api.context.ContextScopeVO;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.FrozenSiBundleVO;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.SiResourceVO;
import top.egon.cola.platform.tianji.agent.application.tool.AgentRuntimeToolExecutionService;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier.ApprovedObservation;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO;
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
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.RuntimeProfileEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.sql.Timestamp;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** One expressly provisioned PostgreSQL database and a controlled external effect. */
@Timeout(90)
class ToolActionProfileIT {
    private static final long TENANT = 41;
    private static final String ISSUER = "https://idp.example/41";
    private static final String HASH = "a".repeat(64);
    private static final String PROMPT_SPEC = "{\"promptText\":\"Approved governed instruction\"}";
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(TENANT, TrustedAgentActor.SubjectType.SERVICE, "process-source", ISSUER);
    private static final TrustedAgentActor EXECUTOR = TrustedAgentActor.verified(TENANT, TrustedAgentActor.SubjectType.SERVICE, "process-executor", ISSUER);
    private static final ToolDescriptor TOOL = new ToolDescriptor("process.approvedStart", 1, "approved_start", "Fixed approval and effect",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
            new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start.invoice", "tianji.api.process.instance.start", HASH, "approval-v1"));
    private static final ToolDescriptor IDEMPOTENT = new ToolDescriptor("process.idempotentStart", 1, "idempotent_start", "Fixed effect",
            TOOL.input(), TOOL.output(), new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.readable",
            ProcessClient.START_RESOURCE_KEY, HASH, null));
    private static final ProcessSchemaVO EMPTY_SCHEMA = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(),
            "required", List.of(), "additionalProperties", false));
    private static final TechnicalLimitsVO LIMITS = new TechnicalLimitsVO(Duration.ofMinutes(3), Duration.ofSeconds(10),
            Duration.ofSeconds(10), 2, 4, 1, 4096, 1024);
    private static Fixture fixture;

    @BeforeAll static void migrate() { fixture = new Fixture(); }

    @Test void replaces_neither_original_profile_nor_consumed_effect_and_rolls_back_approval_with_the_call() throws Exception {
        var runtime = fixture.runtimeRun();
        var legacy = fixture.legacyRun();
        var action = action("race");
        var gate = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var left = pool.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                return fixture.prepare(legacy, action);
            });
            var right = pool.submit(() -> {
                gate.await(10, TimeUnit.SECONDS);
                return fixture.prepare(runtime, action);
            });
            var first = left.get(20, TimeUnit.SECONDS);
            var second = right.get(20, TimeUnit.SECONDS);
            assertEquals(first, second);
            assertEquals("AWAITING_APPROVAL", first.state());
            assertEquals(1, fixture.actionCount(action));
            assertEquals(first.originRunId().equals(runtime.authorization().runId()) ? "RUNTIME_V2" : "LEGACY_V1",
                    fixture.profile(action));
        }
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> fixture.prepare(runtime, new ToolActionRequest(OWNER, TOOL, action.businessKey(), action.businessVersion(), Map.of("amount", 19L)))).code());

        var dispatchable = action("runtime-deterministic");
        var preparedAction = fixture.prepare(runtime, dispatchable);
        assertEquals("RUNTIME_V2", fixture.profile(dispatchable));
        assertEquals(preparedAction, fixture.prepare(legacy, dispatchable), "A legacy observer sees the fixed action but does not take it over");
        var capability = fixture.seedCapability(runtime, TOOL, capabilityFor(runtime, TOOL));
        var request = fixture.call(runtime, dispatchable, capability);
        var prepared = fixture.tx(() -> fixture.receipts.prepare(OWNER, runtime.claim().lease(), runtime.claim().run().revision(), request, () -> {}));
        var proof = approval(dispatchable, fixture.now(), "shared-approval-1");
        var nativeDispatch = new RuntimeCallReceiptBO.DispatchVO(1, "b".repeat(64), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId());
        int callsBefore = fixture.effectCalls.get();
        var fault = new IllegalStateException("Test failure after principal proof consumption");
        assertSame(fault, assertThrows(IllegalStateException.class, () -> fixture.dispatch(runtime, dispatchable, prepared, nativeDispatch, proof, () -> {
            assertEquals("DISPATCHING", fixture.state(dispatchable));
            assertEquals(1, fixture.consumptions(proof));
            throw fault;
        })));
        assertEquals("AWAITING_APPROVAL", fixture.state(dispatchable));
        assertEquals(0, fixture.consumptions(proof));
        assertEquals("PREPARED", fixture.callState(prepared));
        assertEquals(callsBefore, fixture.effectCalls.get());

        fixture.dispatchAuthorized.set(false);
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () ->
                fixture.store.dispatchRuntime(runtime.authorization(), runtime.claim().lease(), runtime.claim().run().revision(),
                        dispatchable, TOOL, prepared, nativeDispatch, proof, () -> {})).code());
        assertEquals(0, fixture.consumptions(proof));
        assertEquals("PREPARED", fixture.callState(prepared));
        fixture.dispatchAuthorized.set(true);
        var dispatched = fixture.store.dispatchRuntime(runtime.authorization(), runtime.claim().lease(), runtime.claim().run().revision(),
                dispatchable, TOOL, prepared, nativeDispatch, proof, () -> {});
        assertEquals("DISPATCHED", fixture.callState(dispatched));
        assertEquals(1, fixture.consumptions(proof));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        fixture.effectCalls.incrementAndGet();
        var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", null);
        var technicalUnknown = new RuntimeCallReceiptBO.OutcomeVO(RuntimeCallReceiptBO.State.UNKNOWN, null, null, null, "OUTCOME_UNKNOWN");
        var wrong = new RuntimeCallReceiptBO.DispatchVO(1, "c".repeat(64), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId());
        assertThrows(AgentLedgerException.class, () -> fixture.finish(runtime, dispatchable, dispatched, wrong, unknown, technicalUnknown));
        var inconsistent = new RuntimeCallReceiptBO.OutcomeVO(RuntimeCallReceiptBO.State.UNKNOWN, null, null, null, "OTHER_REASON");
        assertThrows(AgentLedgerException.class, () ->
                fixture.finish(runtime, dispatchable, dispatched, nativeDispatch, unknown, inconsistent));
        assertEquals("DISPATCHING", fixture.state(dispatchable));
        assertEquals("DISPATCHED", fixture.callState(dispatched));
        fixture.store.finishRuntime(runtime.authorization(), runtime.claim().lease(), runtime.claim().run().revision(),
                dispatchable, TOOL, dispatched, unknown, () -> {});
        assertEquals("UNKNOWN", fixture.callState(dispatched));
        assertEquals("UNKNOWN", fixture.prepare(legacy, dispatchable).state());
        assertEquals("UNKNOWN", fixture.prepare(runtime, dispatchable).state());
        assertThrows(AgentLedgerException.class, () -> fixture.tx(() -> fixture.receipts.prepare(OWNER, runtime.claim().lease(),
                runtime.claim().run().revision(), fixture.call(runtime, dispatchable, capability), () -> {})));
        assertEquals(1, fixture.consumptions(proof));
        assertEquals(callsBefore + 1, fixture.effectCalls.get());
    }

    @Test void disclosesOnlyTheAuthorizedSuccessfulIdempotentResultAndRecordsItsSource() {
        var run = fixture.runtimeRun(IDEMPOTENT, "runtime-disclosure");
        var action = new ToolActionRequest(OWNER, IDEMPOTENT, "disclosure", "business-v1", Map.of("amount", 4L));
        fixture.store.prepare(run.authorization(), run.claim().lease(), action, IDEMPOTENT, fixture.now().plusSeconds(120));
        var binding = capabilityFor(run, IDEMPOTENT);
        var evidence = fixture.seedCapability(run, IDEMPOTENT, binding);
        fixture.applyIdempotentEffect(run, action, evidence);
        var permit = new AtomicBoolean(false);
        var authority = new ToolExecutionAuthorization() {
            @Override public Decision authorize(Request request, Boundary boundary) {
                return permit.get() ? Decision.ALLOW : Decision.DENY;
            }

            @Override public CheckResult check(Request request, Boundary boundary) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(Boundary.RECONCILE, boundary);
                assertEquals(action.intentId(), request.actionIntentId());
                assertEquals(IDEMPOTENT.parametersSha256(action.arguments()), request.parametersSha256());
                if (!permit.get()) return new CheckResult(request, boundary, Decision.DENY, null);
                return new CheckResult(request, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary,
                        Decision.ALLOW, OWNER.subjectType(), evidence.grantId(), null, 1L, 1L, fixture.now(), evidence.expiresAt()));
            }
        };
        var capabilityStore = new ToolCapabilityStore(fixture.source, fixture.ledger);
        capabilityStore.verifyStorage();
        var accessStore = new ToolResultAccessStore(fixture.source, fixture.ledger, capabilityStore, authority);
        accessStore.verifyStorage();
        var access = new ToolResultAccessStore.StartResultAccessRequestVO(binding, run.claim().lease(), IDEMPOTENT, action, UUID.randomUUID());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                assertThrows(AgentLedgerException.class, () -> accessStore.recordRuntimeStartResultAccess(access)).code());
        assertEquals(0, fixture.jdbc.queryForObject("""
                SELECT count(*) FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=? AND call_id=?
                """, Integer.class, TENANT, run.authorization().runId(), access.accessId()));
        permit.set(true);
        var recorded = accessStore.recordRuntimeStartResultAccess(access);
        assertEquals(Map.of("instanceId", "one-instance"), recorded.sourceOutcome().output());
        assertNull(recorded.sourceOutcome().actualCost());
        assertEquals(Map.of("state", "SUCCEEDED", "kind", "TOOL_RESULT_READ", "source_action_revision", recorded.sourceActionRevision(),
                "source_result_sha256", recorded.sourceResultSha256()), fixture.jdbc.queryForMap("""
                SELECT state,kind,source_action_revision,source_result_sha256 FROM tianji_agent.agent_runtime_call_receipt
                WHERE tenant_id=? AND run_id=? AND call_id=?
                """, TENANT, run.authorization().runId(), access.accessId()));
        assertEquals(recorded, accessStore.recordRuntimeStartResultAccess(access));
        permit.set(false);
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                assertThrows(AgentLedgerException.class, () -> accessStore.recordRuntimeStartResultAccess(access)).code());
    }

    @Test void resultReadReplayRequiresFreshAuthorityAndDoesNotConsumeAnotherCall() {
        var run = fixture.runtimeRun(IDEMPOTENT, "runtime-read-replay");
        var action = new ToolActionRequest(OWNER, IDEMPOTENT, "result-read-replay", "business-v1", Map.of("amount", 7L));
        fixture.store.prepare(run.authorization(), run.claim().lease(), action, IDEMPOTENT, fixture.now().plusSeconds(120));
        var evidence = fixture.seedCapability(run, IDEMPOTENT, capabilityFor(run, IDEMPOTENT));
        fixture.applyIdempotentEffect(run, action, evidence);
        var originalAction = fixture.tx(() -> fixture.sql.lockForRuntimeWrite(action, IDEMPOTENT, fixture.now().plusSeconds(5)));
        var fingerprint = new ToolExecutionJsonCodec().sourceResultSha256(action.intentId(), originalAction.revision(),
                action.requestSha256(), originalAction.profile(), IDEMPOTENT, originalAction.outcome());
        var binding = evidence.reservation().binding();
        var lease = run.claim().lease();
        var call = new RuntimeCallReceiptBO().setTenantId(TENANT).setRunId(lease.runId()).setCallId(UUID.randomUUID())
                .setKind(RuntimeCallReceiptBO.Kind.TOOL_RESULT_READ).setOwner(OWNER).setLeaseEpoch(lease.epoch())
                .setCapability(new RuntimeCallReceiptBO.CapabilityVO(UUID.fromString(binding.capabilityId()), binding.bindingSha256(),
                        evidence.reservation().issueEpoch(), evidence.grantId(), IDEMPOTENT.key(), IDEMPOTENT.version(),
                        IDEMPOTENT.reference().contentSha256(), HASH, ToolPolicy.Risk.IDEMPOTENT_WRITE))
                .setInputSha256(IDEMPOTENT.parametersSha256(action.arguments())).setIntentId(UUID.fromString(action.intentId()))
                .setSourceResult(new RuntimeCallReceiptBO.SourceResultVO(originalAction.revision(), fingerprint));
        long revision = run.claim().run().revision();
        var first = fixture.tx(() -> fixture.receipts.recordResultAccess(OWNER, lease, revision,
                authorizeResultRead(call, fixture.now(), HASH), () -> {}));
        assertEquals(RuntimeCallReceiptBO.State.SUCCEEDED, first.getState());
        assertEquals(fingerprint, first.getResultSha256());
        var denial = new IllegalStateException("current authority denied");
        assertSame(denial, assertThrows(IllegalStateException.class, () -> fixture.tx(() ->
                fixture.receipts.recordResultAccess(OWNER, lease, revision, authorizeResultRead(call, fixture.now(), HASH),
                        () -> { throw denial; }))));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () ->
                fixture.tx(() -> fixture.receipts.recordResultAccess(OWNER, lease, revision,
                        authorizeResultRead(call, fixture.now(), "b".repeat(64)), () -> {}))).code());
        var replay = fixture.tx(() -> fixture.receipts.recordResultAccess(OWNER, lease, revision,
                authorizeResultRead(call, fixture.now(), HASH), () -> {}));
        assertEquals(first, replay);
        assertEquals(Map.of("tool_calls", 2, "active_calls", 0), fixture.jdbc.queryForMap("""
                SELECT tool_calls,active_calls FROM tianji_agent.agent_runtime_binding WHERE tenant_id=? AND run_id=?
                """, TENANT, lease.runId()));
    }

    private static RuntimeCallReceiptBO authorizeResultRead(RuntimeCallReceiptBO call, Instant checked, String scope) {
        return call.setDeadlineAt(checked.plusSeconds(5)).setContextMetadata(Map.of("authorization", Map.of(
                "decisionId", UUID.randomUUID().toString(), "checkedAt", checked.toString(),
                "expiresAt", checked.plusSeconds(6).toString(), "scopeSha256", scope, "decision", "ALLOW")));
    }

    @Test void closesDispatchedWriteAndItsOriginalActionWhenTheRunIsCancelled() {
        var run = fixture.runtimeRun(TOOL, "runtime-write-cancellation");
        var action = action("cancel-after-dispatch");
        fixture.prepare(run, action);
        var capability = fixture.seedCapability(run, TOOL, capabilityFor(run, TOOL));
        var prepared = fixture.tx(() -> fixture.receipts.prepare(OWNER, run.claim().lease(), run.claim().run().revision(),
                fixture.call(run, action, capability), () -> {}));
        var dispatch = new RuntimeCallReceiptBO.DispatchVO(1, "f".repeat(64), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId());
        fixture.dispatch(run, action, prepared, dispatch, approval(action, fixture.now(), "cancel"), () -> {});
        var cancellation = fixture.ledger.cancel(OWNER, new CancelAgentRunCommand(run.authorization().runId(), "cancel-after-send",
                run.claim().run().revision(), "user cancellation"));
        assertTrue(cancellation.accepted());
        assertEquals("UNKNOWN", fixture.state(action));
        assertEquals("UNKNOWN", fixture.callState(prepared));
        var cancelled = fixture.ledger.finish(OWNER, run.claim().lease(), cancellation.revision(),
                new AgentExecutionResult(run.authorization().runId(), AgentRunStatusEnum.CANCELLED, Map.of(), null, null, null, null, "UNKNOWN"));
        assertEquals(AgentRunStatusEnum.CANCELLED, cancelled.status());
        assertEquals("UNKNOWN", fixture.state(action));
        assertEquals(Map.of("state", "UNKNOWN", "error_code", "RUNTIME_OUTCOME_UNKNOWN",
                "dispatch_nonce_sha256", dispatch.nonceSha256(), "executor_issuer", dispatch.executorIssuer(),
                "executor_id", dispatch.executorId()), fixture.jdbc.queryForMap("""
                SELECT state,error_code,dispatch_nonce_sha256,executor_issuer,executor_id
                FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=? AND call_id=?
                """, TENANT, run.authorization().runId(), prepared.getCallId()));
        assertEquals(0, fixture.jdbc.queryForObject("""
                SELECT active_calls FROM tianji_agent.agent_runtime_binding WHERE tenant_id=? AND run_id=?
                """, Integer.class, TENANT, run.authorization().runId()));
    }

    @Test void runtimeHostCommitsBeforeTheFixedSendAndReplaysWithoutAnotherEffect() {
        var run = fixture.runtimeRun(IDEMPOTENT, "runtime-tool-host");
        var binding = capabilityFor(run, IDEMPOTENT);
        var evidence = fixture.seedCapability(run, IDEMPOTENT, binding);
        var capabilities = new ToolCapabilityStore(fixture.source, fixture.ledger);
        capabilities.verifyStorage();
        var authorization = new ToolExecutionAuthorization() {
            @Override public Decision authorize(Request request, Boundary boundary) { return Decision.ALLOW; }
            @Override public CheckResult check(Request request, Boundary boundary) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return new CheckResult(request, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary,
                        Decision.ALLOW, OWNER.subjectType(), evidence.grantId(), null, 1L, 1L, fixture.now(), evidence.expiresAt()));
            }
        };
        var actions = new ToolActionIntentStore(fixture.source, fixture.ledger, authorization);
        actions.verifyStorage();
        var results = new ToolResultAccessStore(fixture.source, fixture.ledger, capabilities, authorization);
        results.verifyStorage();
        var sends = new AtomicInteger();
        var sendMarkers = new AtomicInteger();
        var start = new FixedBpmnStartToolTransport() {
            @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
                return callback.apply(new ReadOnlyDecisionToolTransport.VerifiedExecutor(EXECUTOR, evidence.expiresAt()), new OneShotStart() {
                    @Override public StartObservation start() { throw new AssertionError("unbounded call"); }
                    @Override public StartObservation start(Instant deadline, Runnable beforeSend) {
                        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                        assertEquals("DISPATCHED", new JdbcTemplate(fixture.source).queryForObject("""
                                SELECT state FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=? AND call_id=?
                                """, String.class, TENANT, request.run().runId(), request.invocationId()));
                        assertEquals("DISPATCHING", fixture.state(request.action()));
                        assertEquals(0, sends.get());
                        beforeSend.run();
                        assertEquals(1, sendMarkers.get());
                        sends.incrementAndGet();
                        return new StartObservation("tianji:ps-start:v1:" + "9".repeat(64), "one-instance");
                    }
                });
            }
        };
        var boundary = new AgentExecutionContextVO.ToolCallBoundary() {
            @Override public void beforeSend() { sendMarkers.incrementAndGet(); }
            @Override public Map<String, Object> verifyOutput(Map<String, Object> output) {
                assertEquals(Map.of("instanceId", "one-instance"), output);
                return output;
            }
            @Override public void recordKnownNoEffect() { fail("a successful write had an effect"); }
        };
        var host = new AgentRuntimeToolExecutionService(fixture.ledger, capabilities, actions, results, authorization,
                null, null, start, null, (source, ref) -> new AgentRuntimeToolExecutionService.SelectedToolVO(IDEMPOTENT, binding),
                (material, descriptor) -> new AgentRuntimeToolExecutionService.ActionIdentityVO("host-effect", "business-v1", Duration.ofMinutes(2)),
                null, fixture.receipts, fixture.transaction);
        var material = fixture.ledger.runtimeExecutionMaterial(OWNER, new GetAgentRunQuery(run.authorization().runId()));
        var scope = new ContextScopeVO("tianji-agent-runtime", Long.toString(TENANT), OWNER.identityFingerprint(),
                run.authorization().runId(), run.authorization().runId(), "EXECUTE");
        var tool = new AgentExecutionContextVO.ToolReferenceVO(IDEMPOTENT.key(), IDEMPOTENT.version(), IDEMPOTENT.reference().contentSha256());
        var request = new AgentExecutionContextVO.ToolCallRequestVO(run.authorization().runId(), scope, tool,
                Map.of("amount", 11L), true, fixture.now().plusSeconds(8));
        assertEquals(Map.of("instanceId", "one-instance"), host.execute(material, run.claim().lease(), request, boundary, () -> {}));
        assertEquals(Map.of("instanceId", "one-instance"), host.execute(material, run.claim().lease(), request, boundary, () -> {}));
        assertEquals(1, sends.get());
        assertEquals(1, sendMarkers.get());
        assertEquals(List.of("TOOL", "TOOL_RESULT_READ"), fixture.jdbc.queryForList("""
                SELECT kind FROM tianji_agent.agent_runtime_call_receipt
                WHERE tenant_id=? AND run_id=? AND state='SUCCEEDED' ORDER BY kind
                """, String.class, TENANT, run.authorization().runId()));
    }

    @Test void historicalReadReconcilesOnlyPositiveReceiptForTheOriginalRuntimeDispatch() {
        var run = fixture.runtimeRun(TOOL, "runtime-start-recovery");
        var action = action("unknown-start-recovery");
        fixture.prepare(run, action);
        var capability = fixture.seedCapability(run, TOOL, capabilityFor(run, TOOL));
        var prepared = fixture.tx(() -> fixture.receipts.prepare(OWNER, run.claim().lease(), run.claim().run().revision(),
                fixture.call(run, action, capability), () -> {}));
        var dispatch = new RuntimeCallReceiptBO.DispatchVO(1, "e".repeat(64), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId());
        fixture.dispatch(run, action, prepared, dispatch, approval(action, fixture.now(), "recover"), () -> {});
        fixture.ledger.cancel(OWNER, new CancelAgentRunCommand(run.authorization().runId(), "cancel-before-read", run.claim().run().revision(), "cancel"));
        assertEquals("UNKNOWN", fixture.state(action));
        var readAuthorized = new AtomicInteger();
        var positive = new AtomicBoolean();
        var recordedAt = fixture.now();
        var reader = new FixedBpmnStartReceiptRead() {
            @Override public TargetVO bind(Request request) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals(run.authorization(), request.run());
                assertEquals(EXECUTOR, request.executor());
                assertEquals(action.requestSha256(), request.action().requestSha256());
                return new TargetVO(HASH, new GetProcessStartReceiptQuery(action.intentId(), action.requestSha256()));
            }
            @Override public Optional<ObservationVO> read(Request request, TargetVO target, Instant deadline) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return positive.get() ? Optional.of(new ObservationVO(target, EXECUTOR,
                        new ProcessStartReceiptDTO("recovered-instance", ProcessStartReceiptDTO.StatusEnum.APPLIED, recordedAt),
                        "tianji:ps-start:v1:" + "5".repeat(64), deadline)) : Optional.empty();
            }
        };
        var observer = new ToolStartReceiptReconciliationService(fixture.source, fixture.ledger, request -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(EXECUTOR, request.executor());
            assertEquals(run.authorization().runId(), request.dispatchRunId());
            assertEquals(action.intentId(), request.intentId().toString());
            readAuthorized.incrementAndGet();
            return Optional.of(new HistoricalToolReceiptAuthorization.Permit(UUID.randomUUID(), request.bindingSha256(), 1,
                    fixture.now(), request.deadlineAt()));
        }, reader);
        assertEquals(AgentErrorCodeEnum.TIANJI_SIDE_EFFECT_UNKNOWN, assertThrows(AgentLedgerException.class, () ->
                observer.observe(OWNER, run.authorization().runId(), UUID.fromString(action.intentId()))).code());
        assertEquals("UNKNOWN", fixture.callState(prepared));
        positive.set(true);
        var observed = observer.observe(OWNER, run.authorization().runId(), UUID.fromString(action.intentId()));
        assertEquals("recovered-instance", observed.instanceId());
        assertEquals("APPLIED", fixture.state(action));
        assertEquals("SUCCEEDED", fixture.callState(prepared));
        assertEquals(observed, observer.observe(OWNER, run.authorization().runId(), UUID.fromString(action.intentId())));
        assertEquals(5, readAuthorized.get());
    }

    private static ToolActionRequest action(String key) {
        return new ToolActionRequest(OWNER, TOOL, key, "business-v1", Map.of("amount", 17L));
    }

    private static ToolExecutionGrant.BindingVO capabilityFor(Active run, ToolDescriptor descriptor) {
        var subject = new AgentServiceExecutionGrant.Subject(OWNER.subjectId(), "process", "platform", "process", "test",
                URI.create("https://process.example/api"));
        return new ToolExecutionGrant.BindingVO(run.authorization(), descriptor.reference(), HASH, "TIANJI_TOOL_START",
                URI.create("https://tools.example/api"), ProcessClient.START_RESOURCE_KEY, subject);
    }

    private static ApprovedObservation approval(ToolActionRequest action, Instant verified, String key) {
        var binding = new ToolApprovalBinding(OWNER, "approval-v1", "d".repeat(64), "approval-release", "approveInvoice", "e".repeat(64), "review");
        var query = binding.query(action, key);
        var source = query.sourceStart();
        var proof = new VerifiedApprovalDTO(binding.policyRef(), binding.policyBindingSha256(), query.fingerprint(), query.approvalReceiptId(),
                query.requesterType(), query.requesterSubjectId(), source.commandId(), "approval-instance", "approval-task",
                binding.releaseVersion(), binding.definitionKey(), binding.definitionSha256(), binding.taskDefinitionKey(),
                OrchestrationRequestDigests.start(source), true, verified.minusSeconds(1), verified, verified.plusSeconds(25));
        return new ApprovedObservation("process-primary", ISSUER, "https://process.example/api", binding, proof, OWNER, verified.plusSeconds(90));
    }

    private static FrozenSiBundleVO runtimeBundle(AgentRuntimeTemplateCommand template, ToolDescriptor descriptor) {
        String agentSpec = "{\"modelKey\":\"registered-model\",\"systemInstruction\":\"Fixed governed tool root\"}";
        String mcpSpec = mcpSpec(descriptor);
        var root = new VersionIdentityVO(Long.toString(TENANT), "101", "201", "1", DdcSiKindEnum.AGENT, "test", bodySha(agentSpec));
        var prompt = new VersionIdentityVO(Long.toString(TENANT), "102", "202", "1", DdcSiKindEnum.PROMPT, "test", bodySha(PROMPT_SPEC));
        var mcp = new VersionIdentityVO(Long.toString(TENANT), "103", "203", Long.toString(descriptor.version()),
                DdcSiKindEnum.MCP, "test", bodySha(mcpSpec));
        // SI protects the published JSON bytes; the original runtime descriptor digest is
        // independently retained in template/tool grants and is checked by the real tool service.
        assertNotEquals(descriptor.reference().contentSha256(), mcp.digestSha256());
        var manifest = DdcSiDependencyManifestVO.create(root, null, List.of(prompt, mcp), List.of(
                new EdgeVO(root.versionId(), prompt.versionId()), new EdgeVO(root.versionId(), mcp.versionId())));
        return new FrozenSiBundleVO("process", "governed-tools", "test", null, "tianji", manifest, List.of(
                new SiResourceVO(root, template.getTemplateKey(), null, agentSpec),
                new SiResourceVO(prompt, "instructions", null, PROMPT_SPEC),
                new SiResourceVO(mcp, "governed-tool-server", null, mcpSpec)));
    }

    private static String mcpSpec(ToolDescriptor descriptor) {
        var tool = Map.of("toolId", descriptor.key(), "name", descriptor.name(), "description", descriptor.description(),
                "inputSchema", toolSchema(descriptor.input()), "outputSchema", toolSchema(descriptor.output()),
                "requiredPermissions", List.of("tianji:tools:execute"), "riskLevel", "HIGH",
                "idempotent", descriptor.policy().risk() == ToolPolicy.Risk.IDEMPOTENT_WRITE, "enabled", true);
        var spec = Map.ofEntries(Map.entry("schemaVersion", "mcp-server-v2"), Map.entry("sourceType", "INTERNAL_SERVICE"),
                Map.entry("protocolVersion", "2026-07-28"), Map.entry("transport", "STREAMABLE_HTTP"),
                Map.entry("endpoint", "http://tool-fixture.fixture.svc.cluster.local:8080/mcp"),
                Map.entry("serviceBinding", Map.of("bindingId", "controlled-tool-fixture", "revision", "1",
                        "serviceName", "tool-fixture", "namespace", "fixture", "port", 8080)),
                Map.entry("capabilityFingerprint", descriptor.reference().contentSha256()),
                Map.entry("args", List.of()), Map.entry("env", Map.of()), Map.entry("headers", Map.of()),
                Map.entry("tools", List.of(tool)), Map.entry("dependencies", List.of()), Map.entry("resources", List.of()),
                Map.entry("apps", List.of()), Map.entry("remoteMounts", List.of()));
        return new String(ProcessTypedJson.canonicalBytes(spec), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> toolSchema(ToolSchema schema) {
        var properties = new java.util.TreeMap<String, Object>();
        schema.fields().forEach((name, type) -> properties.put(name, Map.of("type", type.name().toLowerCase(java.util.Locale.ROOT))));
        return Map.of("type", "object", "properties", properties, "required", schema.required().stream().sorted().toList(),
                "additionalProperties", false);
    }

    private static String bodySha(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private static final class Fixture {
        private final DriverManagerDataSource source;
        private final JdbcTemplate jdbc;
        private final AgentLedgerService ledger;
        private final ToolActionIntentStore store;
        private final ToolActionIntentSql sql;
        private final AgentRuntimeCallReceiptStore receipts;
        private final TransactionTemplate transaction;
        private final AtomicInteger effectCalls = new AtomicInteger();
        private final AtomicBoolean dispatchAuthorized = new AtomicBoolean(true);

        private Fixture() {
            String url = System.getProperty("tianji.tool.profile.postgres.test.url", "");
            String version = System.getProperty("tianji.tool.profile.postgres.vector.version", "");
            String journalUrl = System.getProperty("tianji.tool.journal.postgres.test.url", "");
            if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}") || url.equals(journalUrl)
                    || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
                throw new IllegalArgumentException("Tool action profile tests require an independent empty loopback database with pinned vector");
            }
            source = new DriverManagerDataSource(url);
            var properties = new Properties();
            properties.setProperty("connectTimeout", "5");
            properties.setProperty("socketTimeout", "15");
            source.setConnectionProperties(properties);
            jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(5);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
            assertEquals(version, jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
            var flyway = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                    .locations("classpath:db/tianji-agent").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                    .target("17").cleanDisabled(true).load();
            assertEquals(17, flyway.migrate().migrationsExecuted);
            flyway.validate();
            ledger = new AgentLedgerService(source);
            transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            transaction.setTimeout(10);
            store = new ToolActionIntentStore(source, ledger, new ToolExecutionAuthorization() {
                @Override public Decision authorize(Request request, Boundary boundary) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(Boundary.PREPARE, boundary);
                    return Decision.ALLOW;
                }

                @Override public CheckResult check(Request request, Boundary boundary) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(Boundary.DISPATCH, boundary);
                    assertEquals(TOOL.parametersSha256(Map.of("amount", 17L)), request.parametersSha256());
                    if (!dispatchAuthorized.get()) return new CheckResult(request, boundary, Decision.DENY, null);
                    return new CheckResult(request, boundary, Decision.ALLOW, new AuditVO(1, UUID.randomUUID(), boundary,
                            Decision.ALLOW, OWNER.subjectType(), "8" + "3".repeat(62), null, 1L, 1L, now(), request.deadlineAt()));
                }
            });
            store.verifyStorage();
            sql = new ToolActionIntentSql(source, ledger);
            receipts = new AgentRuntimeCallReceiptStore(source, new AgentRuntimeBindingStore(source));
        }

        private Active legacyRun() {
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(3), Duration.ofSeconds(10), Duration.ofSeconds(10), 2, 4,
                    4096, 1024, null, null, 1, Set.of(TOOL.versionKey()), "no-retry", "approval-v1");
            ledger.publish(OWNER, new AgentDefinitionSpecDTO("legacy-tools", 1, "CODE_FIRST", "controlled", "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(TOOL.reference()), "model-route", "tool-policy"), policy, Map.of()));
            var queued = ledger.start(OWNER, new StartAgentRunCommand("legacy-tools", 1, null, "legacy-key", "business", "correlation",
                    new AgentInputDTO("controlled", List.of(), Map.of()), null));
            var query = new GetAgentRunQuery(queued.runId());
            var claim = ledger.claim(OWNER, query, queued.revision(), "legacy-executor", Duration.ofMinutes(2));
            return new Active(ledger.executionMaterial(OWNER, query).authorization(), claim);
        }

        private Active runtimeRun() {
            return runtimeRun(TOOL, "runtime-tools");
        }

        private Active runtimeRun(ToolDescriptor descriptor, String key) {
            var template = new AgentRuntimeTemplateCommand(key, 1, RuntimeProfileEnum.RUNTIME_V2, "runtime-executor",
                    EMPTY_SCHEMA, EMPTY_SCHEMA, EMPTY_SCHEMA, new AgentRuntimeTemplateCommand.TemplateConfigurationVO(
                    new AgentRuntimeTemplateCommand.TemplateDependencyRefVO("instructions", 1, bodySha(PROMPT_SPEC)), "registered-model",
                    List.of(new AgentRuntimeTemplateCommand.TemplateDependencyRefVO(descriptor.key(), descriptor.version(), descriptor.reference().contentSha256())), null), LIMITS);
            ledger.publishRuntimeTemplate(OWNER, template, () -> true, (actor, requested) -> clock -> true);
            Instant deadline = now().plusSeconds(160);
            String schema = new AgentJsonCodec().runtimeSchemaHash(EMPTY_SCHEMA);
            var command = new LinkedAgentRuntimeCommand(key, new AgentProcessLink("process-source", WorkerScopeTypeEnum.BPMN,
                    "instance", key, template.contentSha256(), deadline.minusSeconds(40)), "runtime-node-1", key,
                    1, template.contentSha256(), HASH, schema, schema, Map.of(), Map.of(), LIMITS, deadline, "correlation");
            var bundle = runtimeBundle(template, descriptor);
            var queued = tx(() -> ledger.startLinkedRuntimeV2(OWNER, command, bundle));
            var query = new GetAgentRunQuery(queued.runId());
            assertEquals(bundle, ledger.runtimeExecutionMaterial(OWNER, query).siBundle());
            var claim = ledger.claimRuntimeV2(OWNER, query, queued.revision(), "runtime-executor", Duration.ofMinutes(2));
            return new Active(ledger.runtimeExecutionMaterial(OWNER, query).authorization(), claim);
        }

        private ToolExecutionGrant.EvidenceVO seedCapability(Active run, ToolDescriptor descriptor, ToolExecutionGrant.BindingVO binding) {
            var receipt = new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(binding, 1), "8" + "3".repeat(62),
                    "ENC:v1:key:nonce:access", run.claim().lease().expiresAt().minusSeconds(2));
            jdbc.update("""
                    INSERT INTO tianji_agent.agent_tool_capability
                      (tenant_id,run_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,backend_binding_sha256,
                       purpose,resource_uri,authorization_resource_key,capability_id,binding_sha256,issue_epoch,grant_id,ciphertext,expires_at,
                       source_client_id,source_app_id,source_business_code,source_application_code,source_environment,source_resource_uri,
                       created_at,updated_at,deadline_at)
                    VALUES (?,?,'SERVICE',?,?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,?,?,?,?,?,clock_timestamp(),clock_timestamp(),?)
                    """, TENANT, run.authorization().runId(), OWNER.issuerFingerprint(), OWNER.subjectId(), descriptor.key(), descriptor.version(),
                    descriptor.reference().contentSha256(), HASH, binding.purpose(), binding.resource().toASCIIString(), binding.authorizationResourceKey(),
                    UUID.fromString(binding.capabilityId()), binding.bindingSha256(), receipt.grantId(), receipt.ciphertext(), Timestamp.from(receipt.expiresAt()),
                    binding.serviceSubject().clientId(), binding.serviceSubject().appId(), binding.serviceSubject().businessCode(),
                    binding.serviceSubject().applicationCode(), binding.serviceSubject().environment(), binding.serviceSubject().resourceUri().toASCIIString(),
                    Timestamp.from(run.authorization().deadlineAt()));
            return receipt;
        }

        private void applyIdempotentEffect(Active run, ToolActionRequest action, ToolExecutionGrant.EvidenceVO evidence) {
            var binding = evidence.reservation().binding();
            var cap = new RuntimeCallReceiptBO.CapabilityVO(UUID.fromString(binding.capabilityId()), binding.bindingSha256(), 1, evidence.grantId(),
                    IDEMPOTENT.key(), IDEMPOTENT.version(), IDEMPOTENT.reference().contentSha256(), HASH, ToolPolicy.Risk.IDEMPOTENT_WRITE);
            var call = new RuntimeCallReceiptBO().setTenantId(TENANT).setRunId(run.authorization().runId()).setOwner(OWNER).setCallId(UUID.randomUUID())
                    .setKind(RuntimeCallReceiptBO.Kind.TOOL).setLeaseEpoch(run.claim().lease().epoch()).setCapability(cap).setContextMetadata(Map.of())
                    .setIntentId(UUID.fromString(action.intentId())).setInputSha256(IDEMPOTENT.parametersSha256(action.arguments())).setDeadlineAt(evidence.expiresAt());
            var dispatch = new RuntimeCallReceiptBO.DispatchVO(1, "1".repeat(64), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId());
            var lease = run.claim().lease();
            long revision = run.claim().run().revision();
            var dispatched = tx(() -> {
                var prepared = receipts.prepare(OWNER, lease, revision, call, () -> {});
                return receipts.markDispatched(OWNER, lease, revision, prepared.getCallId(), prepared.getRevision(), dispatch, () -> {}, marked ->
                        sql.beginWrite(sql.lockForRuntimeWrite(action, IDEMPOTENT, now().plusSeconds(5)),
                                new ToolActionIntentSql.DispatchVO(marked.getRunId(), null, marked.getCallId(), 1, dispatch.nonceSha256(), EXECUTOR),
                                marked.getDispatchedAt(), now().plusSeconds(5)));
            });
            var success = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "one-instance"),
                    "tianji:ps-start:v1:" + "9".repeat(64), null, null);
            var digest = new ToolExecutionJsonCodec().runtimeWriteResultSha256(action.intentId(), action.requestSha256(), IDEMPOTENT, success);
            tx(() -> receipts.finish(OWNER, lease, revision, dispatched.getCallId(), dispatched.getRevision(), dispatch,
                    new RuntimeCallReceiptBO.OutcomeVO(RuntimeCallReceiptBO.State.SUCCEEDED, digest, null, null, null), () -> {},
                    marked -> sql.completeWrite(sql.lockForRuntimeWrite(action, IDEMPOTENT, now().plusSeconds(5)), success, now(), now().plusSeconds(5))));
        }

        private ToolActionIntentStore.PreparedActionResult prepare(Active run, ToolActionRequest action) {
            return store.prepare(run.authorization(), run.claim().lease(), action, TOOL, now().plusSeconds(120));
        }

        private RuntimeCallReceiptBO call(Active run, ToolActionRequest action, ToolExecutionGrant.EvidenceVO evidence) {
            return new RuntimeCallReceiptBO().setTenantId(TENANT).setRunId(run.authorization().runId()).setCallId(UUID.randomUUID())
                    .setKind(RuntimeCallReceiptBO.Kind.TOOL).setOwner(OWNER).setLeaseEpoch(run.claim().lease().epoch())
                    .setInputSha256(TOOL.parametersSha256(action.arguments())).setContextMetadata(Map.of()).setIntentId(UUID.fromString(action.intentId()))
                    .setCapability(new RuntimeCallReceiptBO.CapabilityVO(UUID.fromString(evidence.reservation().binding().capabilityId()),
                            evidence.reservation().binding().bindingSha256(), evidence.reservation().issueEpoch(), evidence.grantId(),
                            TOOL.key(), TOOL.version(), TOOL.reference().contentSha256(), TOOL.policy().bindingSha256(), TOOL.policy().risk()))
                    .setDeadlineAt(evidence.expiresAt());
        }

        private RuntimeCallReceiptBO dispatch(Active run, ToolActionRequest action, RuntimeCallReceiptBO call,
                                              RuntimeCallReceiptBO.DispatchVO dispatch, ApprovedObservation approval, Runnable after) {
            return tx(() -> receipts.markDispatched(OWNER, run.claim().lease(), run.claim().run().revision(), call.getCallId(),
                    call.getRevision(), dispatch, () -> {}, marked -> {
                        var row = sql.lockForRuntimeApproval(action, TOOL, now().plusSeconds(8));
                        sql.beginApprovedWrite(row, approval, new ToolActionIntentSql.DispatchVO(marked.getRunId(), null, marked.getCallId(),
                                dispatch.epoch(), dispatch.nonceSha256(), EXECUTOR), marked.getDispatchedAt(), now().plusSeconds(8));
                        after.run();
                    }));
        }

        private RuntimeCallReceiptBO finish(Active run, ToolActionRequest action, RuntimeCallReceiptBO call,
                                            RuntimeCallReceiptBO.DispatchVO dispatch, ToolExecutionOutcome outcome,
                                            RuntimeCallReceiptBO.OutcomeVO technical) {
            return tx(() -> receipts.finish(OWNER, run.claim().lease(), run.claim().run().revision(), call.getCallId(),
                    call.getRevision(), dispatch, technical, () -> {}, marked -> {
                        var row = sql.lockForRuntimeApproval(action, TOOL, now().plusSeconds(8));
                        sql.completeApprovedWrite(row, outcome, now(), now().plusSeconds(8));
                    }));
        }

        private int actionCount(ToolActionRequest request) {
            return jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?",
                    Integer.class, TENANT, UUID.fromString(request.intentId()));
        }
        private String profile(ToolActionRequest request) { return actionText(request, "action_profile"); }
        private String state(ToolActionRequest request) { return actionText(request, "state"); }
        private String actionText(ToolActionRequest request, String column) {
            return jdbc.queryForObject("SELECT " + column + " FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND intent_id=?",
                    String.class, TENANT, UUID.fromString(request.intentId()));
        }
        private String callState(RuntimeCallReceiptBO call) {
            return jdbc.queryForObject("SELECT state FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=? AND call_id=?",
                    String.class, TENANT, call.getRunId(), call.getCallId());
        }
        private int consumptions(ApprovedObservation approval) {
            return jdbc.queryForObject("""
                    SELECT count(*) FROM tianji_agent.agent_tool_action_intent
                    WHERE tenant_id=? AND approval_source_namespace_sha256=? AND approval_receipt_id=?
                    """, Integer.class, TENANT, approval.sourceNamespaceSha256(), approval.approval().approvalReceiptId());
        }
        private Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
        private <T> T tx(Supplier<T> work) { return transaction.execute(status -> work.get()); }
    }

    private record Active(AgentRunAuthorization.Request authorization, ClaimedAgentRun claim) { }
}
