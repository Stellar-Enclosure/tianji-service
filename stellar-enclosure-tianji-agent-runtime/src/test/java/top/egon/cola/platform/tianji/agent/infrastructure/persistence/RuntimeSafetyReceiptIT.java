package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.adapter.springai.SpringAiExecutionModelAdapter;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionResult.SideEffectOutcomeEnum;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionResult.StatusEnum;
import top.egon.cola.component.agentflow.context.DefaultContextAssembler;
import top.egon.cola.component.agentflow.execution.DefaultAgentExecutor;
import top.egon.cola.component.agentflow.session.InMemorySessionStore;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextConverterImpl;
import top.egon.cola.platform.tianji.agent.application.run.AgentRuntimeExecutionService;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.RuntimeBindingBO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.RuntimeProfileEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateConfigurationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateDependencyRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private static final long V2_TEST_TENANT = 31;
    private static final TrustedAgentActor V2_SERVICE = TrustedAgentActor.verified(V2_TEST_TENANT, TrustedAgentActor.SubjectType.SERVICE,
            "process-dispatcher", "https://idp.example");
    private static final ProcessSchemaVO V2_INPUT = ProcessSchemaVO.fromWire(Map.of("type", "object",
            "properties", Map.of("amount", Map.of("type", "integer")), "required", List.of("amount"), "additionalProperties", false));
    private static final ProcessSchemaVO V2_EMPTY = ProcessSchemaVO.fromWire(Map.of("type", "object",
            "properties", Map.of(), "required", List.of(), "additionalProperties", false));
    private static final TechnicalLimitsVO V2_LIMITS = new TechnicalLimitsVO(Duration.ofMinutes(3), Duration.ofSeconds(10),
            Duration.ofSeconds(10), 2, 2, 1, 4096, 1024);
    private static final AgentRuntimeTemplateCommand V2_TEMPLATE = new AgentRuntimeTemplateCommand("billing", 1,
            RuntimeProfileEnum.RUNTIME_V2, "agent-executor", V2_INPUT, V2_EMPTY, V2_EMPTY,
            new TemplateConfigurationVO(new TemplateDependencyRefVO("approved-instructions", 1, HASH), "registered-model", List.of(), null),
            V2_LIMITS);
    private static RuntimeCompatibilityIT.Fixture database;
    private static AgentRuntimeBindingStore bindings;
    private static AgentRuntimeCallReceiptStore calls;
    private static TransactionTemplate transaction;
    private static JdbcTemplate runtime;
    private static AgentLedgerService v2Ledger;

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
        String grantee = "\"" + source.getUser() + "\"";
        database.admin().execute("GRANT SELECT ON tianji_agent.agent_definition, tianji_agent.agent_definition_version TO " + grantee);
        database.admin().execute("GRANT INSERT, UPDATE ON tianji_agent.agent_run TO " + grantee);
        database.admin().execute("GRANT SELECT, INSERT ON tianji_agent.agent_event TO " + grantee);
        database.admin().execute("GRANT SELECT, INSERT, UPDATE ON tianji_agent.agent_run_command_receipt TO " + grantee);
        database.admin().execute("GRANT INSERT ON tianji_agent.agent_definition, tianji_agent.agent_definition_version TO " + grantee);
        v2Ledger = new AgentLedgerService(source);
        database.admin().update("""
                INSERT INTO tianji_agent.agent_definition (tenant_id,definition_key,active_version,created_at)
                VALUES (?,?,?,clock_timestamp())
                """, V2_TEST_TENANT, V2_TEMPLATE.getTemplateKey(), V2_TEMPLATE.getTemplateVersion());
        database.admin().update("""
                INSERT INTO tianji_agent.agent_definition_version (tenant_id,definition_key,version,content_sha256,specification,created_at)
                VALUES (?,?,?,?,?::jsonb,clock_timestamp())
                """, V2_TEST_TENANT, V2_TEMPLATE.getTemplateKey(), V2_TEMPLATE.getTemplateVersion(), V2_TEMPLATE.contentSha256(),
                new String(V2_TEMPLATE.canonicalBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void actualProviderSubscriptionSeesCommittedReceiptAndValidatedUsageIsStored() {
        var run = startRuntime(linkedRuntimeCommand("provider-receipt"));
        var providerCalls = new AtomicInteger();
        Clock clock = Clock.systemUTC();
        try (var validatorFactory = jakarta.validation.Validation.buildDefaultValidatorFactory();
             var timers = new ScheduledThreadPoolExecutor(1);
             var control = new ScheduledThreadPoolExecutor(1)) {
            var validator = validatorFactory.getValidator();
            var adapter = new SpringAiExecutionModelAdapter();
            var provider = new ChatModel() {
                @Override public ChatResponse call(Prompt prompt) { throw new AssertionError("Streaming model required"); }
                @Override public ChatOptions getOptions() { return ChatOptions.builder().model("fixed-model").build(); }
                @Override public Flux<ChatResponse> stream(Prompt prompt) {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    var row = runtime.queryForMap("""
                            SELECT state,input_sha256,context_metadata->>'modelRef' AS model_ref
                            FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=?
                            """, V2_TEST_TENANT, run.runId());
                    assertEquals("DISPATCHED", row.get("state"));
                    assertEquals("registered-model", row.get("model_ref"));
                    assertTrue(row.get("input_sha256").toString().matches("[0-9a-f]{64}"));
                    providerCalls.incrementAndGet();
                    return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("{}"))),
                            ChatResponseMetadata.builder().usage(new DefaultUsage(20, 4, 24)).build()));
                }
            };
            var registered = new SpringAiExecutionModelAdapter.ModelRegistrationVO("registered-model", "fixed-model",
                    provider, 4096, 1024, input -> 20);
            var core = new DefaultAgentExecutor(clock, validator, new DefaultContextAssembler(clock, validator), timers,
                    new InMemorySessionStore(clock, validator), Map.of("agent-executor", invocation -> {
                        adapter.generate(invocation, registered, new Prompt("bounded test request"));
                        return new top.egon.cola.component.agentflow.api.execution.AgentExecutionResult(run.runId(),
                                StatusEnum.COMPLETED, Map.of(), null, SideEffectOutcomeEnum.NONE, clock.instant());
                    }));
            var service = new AgentRuntimeExecutionService(v2Ledger, (request, boundary) -> AgentRunAuthorization.Decision.ALLOW,
                    core, new TianjiHostContextConverterImpl(), control, calls, transaction);
            assertEquals(AgentRunStatusEnum.COMPLETED, service.executeQueued(V2_SERVICE, new GetAgentRunQuery(run.runId())).status());
        }
        assertEquals(1, providerCalls.get());
        assertEquals(Map.of("state", "SUCCEEDED", "input_tokens", 20L, "output_tokens", 4L), runtime.queryForMap("""
                SELECT state,input_tokens,output_tokens FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=?
                """, V2_TEST_TENANT, run.runId()));
        assertEquals(0, runtime.queryForObject("SELECT active_calls FROM tianji_agent.agent_runtime_binding WHERE tenant_id=? AND run_id=?",
                Integer.class, V2_TEST_TENANT, run.runId()));
    }

    @Test
    void runtimeGrantRequiresTheSamePinnedOccurrenceUntilTheFenceCloses() {
        var grantLedger = new AgentLedgerService(database.source());
        var submissions = new AgentLinkedSubmissionService(database.source(), grantLedger);
        var command = linkedRuntimeCommand("runtime-grant");
        var run = submissions.submitRuntimeV2(V2_SERVICE, command, () -> true);
        var store = new AgentServiceExecutionGrantStore(database.source(), grantLedger);
        var resource = URI.create("https://runtime.example/tianji");
        var subject = new AgentServiceExecutionGrant.Subject(V2_SERVICE.subjectId(), "process", "platform",
                "automation", "test", URI.create("https://process.example/tianji"));
        var reservation = store.reserve(V2_SERVICE, run.runId(), resource, subject).orElseThrow();
        var evidence = v2Ledger.linkedRunEvidence(V2_SERVICE, new GetAgentRunQuery(run.runId()));
        assertEquals(command.getLink(), evidence.processLink());
        assertEquals(command.getTemplateSha256(), reservation.authorization().definitionSha256());
        assertEquals(evidence.authorization(), reservation.authorization());
        var ticket = new AgentServiceExecutionGrant.Ticket("8" + "9".repeat(62), "ENC:v1:key:nonce:v2",
                now(runtime).plusSeconds(25));
        assertTrue(store.complete(reservation, ticket));
        assertEquals(ticket, store.current(evidence.authorization(), resource, subject).orElseThrow());
        var otherOccurrence = new AgentProcessLink(command.getLink().sourceId(), command.getLink().scopeType(),
                command.getLink().scopeInstanceId(), "wrong-occurrence", command.getTemplateSha256(), command.getLink().dispatchNotAfter());
        assertTrue(submissions.resolveRuntimeV2(V2_SERVICE, new LinkedAgentLookupQuery(command.getIdempotencyKey(), otherOccurrence)).isEmpty());
        var query = new LinkedAgentLookupQuery(command.getIdempotencyKey(), command.getLink());
        assertTrue(submissions.closeRuntimeV2(V2_SERVICE, query).closed());
        assertTrue(store.current(evidence.authorization(), resource, subject).isEmpty());
        assertFalse(store.complete(reservation, ticket));

        var legacyActor = TrustedAgentActor.verified(32, TrustedAgentActor.SubjectType.SERVICE, V2_SERVICE.subjectId(),
                "https://idp.example");
        var legacy = grantLedger.publish(legacyActor, AgentJsonCodecTest.spec(1, Map.of(), Set.of()));
        var legacyCommand = new StartAgentRunCommand("review", 1, null, "legacy-grant", "business", "trace",
                new AgentInputDTO("legacy fixture", List.of(), Map.of()), null);
        var legacyLink = new AgentProcessLink("process-source", WorkerScopeTypeEnum.BPMN, "legacy-instance", "legacy-occurrence",
                legacy.contentSha256(), now(runtime).plusSeconds(40).truncatedTo(ChronoUnit.MICROS));
        var legacyRun = submissions.submit(legacyActor, new LinkedAgentStartCommand(legacyCommand, legacyLink));
        var legacyGrant = store.reserve(legacyActor, legacyRun.runId(), resource, subject).orElseThrow();
        var oldMaterial = grantLedger.executionMaterial(legacyActor, new GetAgentRunQuery(legacyRun.runId()));
        assertEquals(oldMaterial.authorization(), legacyGrant.authorization());
        var legacyTicket = new AgentServiceExecutionGrant.Ticket("8" + "1".repeat(62), "ENC:v1:key:nonce:legacy",
                now(runtime).plusSeconds(10));
        assertTrue(store.complete(legacyGrant, legacyTicket));
        assertEquals(legacyTicket, store.current(oldMaterial.authorization(), resource, subject).orElseThrow());
    }

    @Test
    void linkedRuntimeStartFreezesSnapshotBindingAndEventInOneOwningTransaction() {
        var restoredStart = new AtomicReference<AgentRunDTO>();
        var rolledBack = linkedRuntimeCommand("atomic-rollback");
        var failure = new IllegalStateException("Test forced rollback after linked start");
        var thrown = assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            restoredStart.set(v2Ledger.startLinkedRuntimeV2(V2_SERVICE, rolledBack));
            throw failure;
        }));
        assertEquals(failure, thrown);
        assertNotNull(restoredStart.get());
        assertEquals(List.of(0, 0, 0), runtimeFacts(restoredStart.get().runId()));

        var command = linkedRuntimeCommand("pin-exact");
        var run = startRuntime(command);
        assertEquals(AgentRunStatusEnum.QUEUED, run.status());
        assertEquals(List.of(1, 1, 1), runtimeFacts(run.runId()));
        assertEquals(run, startRuntime(command));
        assertEquals(List.of(1, 1, 1), runtimeFacts(run.runId()));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> startRuntime(linkedRuntimeCommand(command.getIdempotencyKey(), command.getAttemptDeadlineAt())
                        .setInput(Map.of("amount", 12)))).code());
        assertFalse(run.deadlineAt().isAfter(command.getAttemptDeadlineAt()));
        runtime.queryForObject("""
                SELECT r.execution_snapshot->>'runtimeProfile' AS profile,
                    r.execution_snapshot->>'snapshotSha256' AS snapshot_sha256,b.snapshot_sha256 AS binding_sha256,
                    b.template_sha256,b.configuration_sha256,b.node_execution_ref,b.attempt_deadline_at
                FROM tianji_agent.agent_run r JOIN tianji_agent.agent_runtime_binding b USING (tenant_id,run_id)
                WHERE r.tenant_id=? AND r.run_id=?
                """, (row, index) -> {
            assertEquals("RUNTIME_V2", row.getString("profile"));
            assertTrue(row.getString("snapshot_sha256").matches("[0-9a-f]{64}"));
            assertEquals(row.getString("snapshot_sha256"), row.getString("binding_sha256"));
            assertEquals(command.getTemplateSha256(), row.getString("template_sha256"));
            assertEquals(command.getConfigurationSha256(), row.getString("configuration_sha256"));
            assertEquals(command.getNodeExecutionRef(), row.getString("node_execution_ref"));
            assertEquals(command.getAttemptDeadlineAt(), row.getObject("attempt_deadline_at", OffsetDateTime.class).toInstant());
            return true;
        }, V2_TEST_TENANT, run.runId());

        assertEquals(1, database.admin().update("""
                UPDATE tianji_agent.agent_runtime_binding SET configuration_sha256=?
                WHERE tenant_id=? AND run_id=?
                """, "b".repeat(64), V2_TEST_TENANT, run.runId()));
        assertCorruptRuntimeRunRejectsMutation(command, run);
        assertEquals(1, database.admin().update("""
                DELETE FROM tianji_agent.agent_runtime_binding WHERE tenant_id=? AND run_id=?
                """, V2_TEST_TENANT, run.runId()));
        assertCorruptRuntimeRunRejectsMutation(command, run);

        var cappedCommand = linkedRuntimeCommand("pin-deadline-cap").setLimits(new TechnicalLimitsVO(
                Duration.ofSeconds(45), Duration.ofSeconds(10), Duration.ofSeconds(10), 2, 2, 1, 4096, 1024));
        var capped = startRuntime(cappedCommand);
        assertEquals(capped.createdAt().plusSeconds(45), capped.deadlineAt());
        assertTrue(capped.deadlineAt().isBefore(cappedCommand.getAttemptDeadlineAt()));
        assertEquals(capped, startRuntime(cappedCommand));
        var pinned = v2Ledger.runtimeExecutionMaterial(V2_SERVICE, new GetAgentRunQuery(capped.runId()));
        assertEquals(cappedCommand.getAttemptDeadlineAt(), pinned.command().getAttemptDeadlineAt());
        assertEquals(capped.deadlineAt(), pinned.authorization().deadlineAt());
        assertEquals(capped.deadlineAt(), runtime.queryForObject("""
                SELECT attempt_deadline_at FROM tianji_agent.agent_runtime_binding WHERE tenant_id=? AND run_id=?
                """, (row, index) -> row.getObject(1, OffsetDateTime.class).toInstant(), V2_TEST_TENANT, capped.runId()));
    }

    @Test
    void runtimeLifecycleNeverTouchesLegacyBudgetWhileLegacyStillMarksOutstandingUnknown() {
        assertEquals(Boolean.FALSE, runtime.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM unnest(ARRAY['SELECT','INSERT','UPDATE','DELETE']) privilege
                    WHERE has_table_privilege('tianji_agent.agent_budget_reservation',privilege))
                """, Boolean.class));
        var completion = claimRuntime("life-finish");
        var completed = v2Ledger.finish(V2_SERVICE, completion.lease(), completion.run().revision(), complete(completion.run()));
        assertEquals(AgentRunStatusEnum.COMPLETED, completed.status());

        var cancel = startRuntime(linkedRuntimeCommand("life-cancel"));
        var cancelCommand = new CancelAgentRunCommand(cancel.runId(), "queued-stop", cancel.revision(), "cancel queued");
        var cancelled = v2Ledger.cancel(V2_SERVICE, cancelCommand);
        assertEquals(AgentRunStatusEnum.CANCELLED, cancelled.status());
        assertEquals(cancelled, v2Ledger.cancel(V2_SERVICE, cancelCommand));

        var lost = claimRuntime("life-lost-lease");
        assertEquals(1, database.admin().update("""
                UPDATE tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second'
                WHERE tenant_id=? AND run_id=?
                """, V2_TEST_TENANT, lost.run().runId()));
        assertEquals(AgentRunStatusEnum.SUSPENDED, v2Ledger.suspendExpiredLease(V2_SERVICE,
                new GetAgentRunQuery(lost.run().runId()), lost.run().revision()).status());

        var noPermission = claimRuntime("life-lost-permission");
        assertEquals(AgentRunStatusEnum.SUSPENDED, v2Ledger.suspendForAuthorization(V2_SERVICE,
                noPermission.lease(), AgentRunAuthorization.Decision.DENY).status());
        assertPendingRuntimeCallsCloseOnLeaseLoss();

        Instant deadline = now(runtime).plusSeconds(6);
        var expiredQueued = startRuntime(linkedRuntimeCommand("life-expiry-queued", deadline, deadline.minusMillis(250)));
        var expiredRunning = claimRuntime(linkedRuntimeCommand("life-expiry-running", deadline, deadline.minusMillis(250)));
        var overdueCancel = startRuntime(linkedRuntimeCommand("life-expiry-cancel", deadline, deadline.minusMillis(250)));
        runtime.queryForObject("SELECT 1 FROM pg_sleep(GREATEST(0,EXTRACT(EPOCH FROM (?::timestamptz-clock_timestamp()))))",
                Integer.class, deadline.atOffset(java.time.ZoneOffset.UTC));
        assertEquals(AgentRunStatusEnum.TIMED_OUT, v2Ledger.expireDeadline(V2_SERVICE, new GetAgentRunQuery(expiredQueued.runId()),
                expiredQueued.revision()).status());
        assertEquals(AgentRunStatusEnum.TIMED_OUT, v2Ledger.expireDeadline(V2_SERVICE, expiredRunning.lease(),
                new GetAgentRunQuery(expiredRunning.run().runId()), expiredRunning.run().revision(), "NONE").status());
        assertEquals(AgentRunStatusEnum.TIMED_OUT, v2Ledger.cancel(V2_SERVICE, new CancelAgentRunCommand(overdueCancel.runId(),
                "overdue-stop", overdueCancel.revision(), "cancel expired")).status());

        var v1Owner = TrustedAgentActor.verified(V2_TEST_TENANT, TrustedAgentActor.SubjectType.USER, "legacy-user", "https://idp.example");
        var v1 = new AgentLedgerService(database.source());
        v1.publish(v1Owner, AgentJsonCodecTest.spec(1, Map.of(), Set.of()));
        var legacy = v1.start(v1Owner, new StartAgentRunCommand("review", 1, null, "legacy-budget", "business", "correlation",
                new AgentInputDTO("historical", List.of(), Map.of()), null));
        var legacyClaim = v1.claim(v1Owner, new GetAgentRunQuery(legacy.runId()), legacy.revision(), "legacy-executor", Duration.ofSeconds(20));
        assertEquals(1, database.admin().update("""
                INSERT INTO tianji_agent.agent_budget_reservation
                  (tenant_id,run_id,reservation_id,lease_epoch,kind,input_tokens,output_tokens,state,created_at)
                VALUES (?,?,?,?,'MODEL',1,1,'RESERVED',clock_timestamp())
                """, V2_TEST_TENANT, legacy.runId(), "original-budget", legacyClaim.lease().epoch()));
        assertEquals(AgentRunStatusEnum.COMPLETED, v1.finish(v1Owner, legacyClaim.lease(),
                legacyClaim.run().revision(), complete(legacyClaim.run())).status());
        assertEquals("UNKNOWN", database.admin().queryForObject("""
                SELECT state FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id='original-budget'
                """, String.class, V2_TEST_TENANT, legacy.runId()));
    }

    @Test
    void runtimeTemplatePublicationUsesOriginalReceiptBeforeAdmissionAndDependencyChecks() {
        var template = new AgentRuntimeTemplateCommand("billing-new", 1, RuntimeProfileEnum.RUNTIME_V2, "agent-executor",
                V2_INPUT, V2_EMPTY, V2_EMPTY, V2_TEMPLATE.getConfiguration(), V2_LIMITS);
        var observations = new AtomicInteger();
        var exactAuthority = (AgentLedgerService.RuntimeTemplatePublicationAuthority) (actor, requested) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(V2_SERVICE, actor);
            assertEquals(template, requested);
            observations.incrementAndGet();
            return databaseNow -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return databaseNow.isAfter(Instant.EPOCH);
            };
        };
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, assertThrows(AgentLedgerException.class,
                () -> v2Ledger.publishRuntimeTemplate(V2_SERVICE, template, () -> false, exactAuthority)).code());
        assertEquals(0, observations.get());
        var original = v2Ledger.publishRuntimeTemplate(V2_SERVICE, template, () -> true, exactAuthority);
        assertEquals(template.contentSha256(), original.getContentSha256());
        assertTrue(original.isPublishedAtValid());
        assertEquals(template, v2Ledger.readVerifiedRuntimeTemplate(V2_SERVICE, template.getTemplateKey(), 1, template.contentSha256()));
        assertEquals(original, v2Ledger.publishRuntimeTemplate(V2_SERVICE, template, () -> false, (actor, request) -> {
            throw new AssertionError("Original template replay must not consult publication dependencies");
        }));
        var conflicting = new AgentRuntimeTemplateCommand(template.getTemplateKey(), 1, RuntimeProfileEnum.RUNTIME_V2,
                "different-executor", V2_INPUT, V2_EMPTY, V2_EMPTY, template.getConfiguration(), V2_LIMITS);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEFINITION_VERSION_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> v2Ledger.publishRuntimeTemplate(V2_SERVICE, conflicting, () -> false, exactAuthority)).code());
        assertEquals(1, observations.get());
        assertNull(database.admin().queryForObject("""
                SELECT active_version FROM tianji_agent.agent_definition WHERE tenant_id=? AND definition_key=?
                """, Long.class, V2_TEST_TENANT, template.getTemplateKey()));
    }

    private static void assertPendingRuntimeCallsCloseOnLeaseLoss() {
        var active = claimRuntime("life-close-receipts");
        var lease = active.lease();
        var dispatched = request(new Run(V2_SERVICE, lease, lease.expiresAt()), UUID.randomUUID());
        long revision = active.run().revision();
        var first = inTransaction(() -> calls.prepare(V2_SERVICE, lease, revision, dispatched, PERMITTED));
        inTransaction(() -> calls.markDispatched(V2_SERVICE, lease, revision, first.getCallId(), first.getRevision(), null, PERMITTED));
        assertEquals(AgentRunStatusEnum.SUSPENDED, v2Ledger.suspendForAuthorization(V2_SERVICE,
                lease, AgentRunAuthorization.Decision.UNAVAILABLE).status());
        assertEquals("UNKNOWN", runtime.queryForObject("""
                SELECT state FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=? AND call_id=?
                """, String.class, V2_TEST_TENANT, lease.runId(), first.getCallId()));
        assertEquals(List.of(1, 0, 0), counters(new Run(V2_SERVICE, lease, lease.expiresAt())));
    }

    private static top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun claimRuntime(String key) {
        return claimRuntime(linkedRuntimeCommand(key));
    }

    private static top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun claimRuntime(LinkedAgentRuntimeCommand command) {
        var run = startRuntime(command);
        return v2Ledger.claimRuntimeV2(V2_SERVICE, new GetAgentRunQuery(run.runId()), run.revision(),
                "runtime-executor", Duration.ofSeconds(25));
    }

    private static AgentExecutionResult complete(AgentRunDTO run) {
        return new AgentExecutionResult(run.runId(), AgentRunStatusEnum.COMPLETED, Map.of(), null, null, null, null, "NONE");
    }

    private static void assertCorruptRuntimeRunRejectsMutation(LinkedAgentRuntimeCommand command, AgentRunDTO run) {
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                assertThrows(AgentLedgerException.class, () -> startRuntime(command)).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                assertThrows(AgentLedgerException.class, () -> v2Ledger.claim(V2_SERVICE, new GetAgentRunQuery(run.runId()),
                        run.revision(), "verified-executor", Duration.ofSeconds(20))).code());
        assertEquals(run.revision(), runtime.queryForObject("""
                SELECT revision FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?
                """, Long.class, V2_TEST_TENANT, run.runId()));
    }

    private static AgentRunDTO startRuntime(LinkedAgentRuntimeCommand command) {
        return inTransaction(() -> v2Ledger.startLinkedRuntimeV2(V2_SERVICE, command));
    }

    private static LinkedAgentRuntimeCommand linkedRuntimeCommand(String key) {
        return linkedRuntimeCommand(key, now(runtime).plusSeconds(120));
    }

    private static LinkedAgentRuntimeCommand linkedRuntimeCommand(String key, Instant deadline) {
        return linkedRuntimeCommand(key, deadline, deadline.minusSeconds(30));
    }

    private static LinkedAgentRuntimeCommand linkedRuntimeCommand(String key, Instant deadline, Instant submitBy) {
        return new LinkedAgentRuntimeCommand(key, new AgentProcessLink("process-source", WorkerScopeTypeEnum.BPMN,
                "process-instance", "occurrence-" + key, V2_TEMPLATE.contentSha256(), submitBy),
                "node-" + key, V2_TEMPLATE.getTemplateKey(), V2_TEMPLATE.getTemplateVersion(), V2_TEMPLATE.contentSha256(),
                HASH, rawSchemaSha256(V2_INPUT), rawSchemaSha256(V2_EMPTY), Map.of("amount", 11), Map.of(), V2_LIMITS, deadline, "corr-" + key);
    }

    private static String rawSchemaSha256(ProcessSchemaVO schema) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ProcessTypedJson.canonicalBytes(schema.toWire())));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static List<Integer> runtimeFacts(String runId) {
        return runtime.queryForObject("""
                SELECT (SELECT count(*) FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?),
                       (SELECT count(*) FROM tianji_agent.agent_runtime_binding WHERE tenant_id=? AND run_id=?),
                       (SELECT count(*) FROM tianji_agent.agent_event WHERE tenant_id=? AND run_id=? AND event_type='RUN_QUEUED')
                """, (row, index) -> List.of(row.getInt(1), row.getInt(2), row.getInt(3)),
                V2_TEST_TENANT, runId, V2_TEST_TENANT, runId, V2_TEST_TENANT, runId);
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
