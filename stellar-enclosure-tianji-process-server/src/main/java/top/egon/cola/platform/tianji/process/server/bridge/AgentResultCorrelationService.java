package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Applies an authenticated durable result in the same local transaction as native Message delivery and its execution audit. */
public final class AgentResultCorrelationService {
    private static final Logger log = LoggerFactory.getLogger(AgentResultCorrelationService.class);
    private final SpringProcessEngineConfiguration process;
    private final FlowableWorkerService workers;
    private final AgentResultInbox inbox;
    private final ProcessCommandReceiptStore receipts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Duration retryDelay;
    private final int maxFailures;
    private final TypedParticipantsVO typed;

    private static final ProcessSchemaVO ARTIFACT_SCHEMA = ProcessSchemaVO.fromWire(Map.of(
            "type", "object", "additionalProperties", false,
            "properties", Map.of(
                    "resourceKey", Map.of("type", "string", "maxLength", 128, "pattern", "^[A-Za-z][A-Za-z0-9_.:-]{0,127}$"),
                    "contentSha256", Map.of("type", "string", "maxLength", 64, "pattern", "^[0-9a-f]{64}$"),
                    "mediaType", Map.of("type", "string", "maxLength", 127, "pattern", "^[a-z0-9.+-]{1,63}/[a-z0-9.+-]{1,63}$"),
                    "sizeBytes", Map.of("type", "integer", "minimum", 0, "maximum", 999999999999999L)),
            "required", List.of("resourceKey", "contentSha256", "mediaType", "sizeBytes")));

    public AgentResultCorrelationService(SpringProcessEngineConfiguration process, FlowableWorkerService workers, AgentResultInbox inbox,
                                          Duration retryDelay, int maxFailures) {
        this(process, workers, inbox, retryDelay, maxFailures, null);
    }

    public AgentResultCorrelationService(SpringProcessEngineConfiguration process, FlowableWorkerService workers, AgentResultInbox inbox,
                                          Duration retryDelay, int maxFailures, TypedParticipantsVO typed) {
        this.process = Objects.requireNonNull(process, "process");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null
                || process.getRuntimeService() == null || retryDelay == null || retryDelay.compareTo(Duration.ofMillis(100)) < 0
                || retryDelay.compareTo(Duration.ofHours(1)) > 0 || retryDelay.toNanos() % 1_000_000 != 0 || maxFailures < 1 || maxFailures > 1000) throw invalid();
        FlowableAgentWaitInspector.requireHistory(process);
        workers.requireStorage(process, manager);
        inbox.requireStorage(manager.getDataSource(), manager, process.getDatabaseSchema());
        this.transaction = new TransactionTemplate(manager);
        this.jdbc = new JdbcTemplate(manager.getDataSource());
        this.receipts = new ProcessCommandReceiptStore(jdbc, process.getDatabaseSchema());
        this.retryDelay = retryDelay;
        this.maxFailures = maxFailures;
        this.typed = typed;
    }

    public AgentResultReceipt apply(AgentResultProducerRegistry.Registration registration, String eventId) {
        requireOwnTransaction();
        try {
            return Objects.requireNonNull(transaction.execute(status -> applyInTransaction(registration, eventId)));
        } catch (RuntimeException failure) {
            try {
                transaction.executeWithoutResult(status -> {
                    var initial = inbox.find(registration, eventId, false);
                    if (initial.eventV2() != null) {
                        requireTyped().guard().lockKnownRoot(initial.tenantId(), initial.link().scopeInstanceId());
                    }
                    inbox.lockDispatch(registration, initial);
                    var current = inbox.find(registration, eventId, true);
                    if (current.receipt().state() == AgentResultReceiptStateEnum.RECEIVED) {
                        inbox.reschedule(registration, current, retryDelay, maxFailures, true);
                    }
                });
            } catch (RuntimeException unavailable) {
                log.warn("Agent result retry persistence failed: type={}", unavailable.getClass().getName());
            }
            throw failure;
        }
    }

    private AgentResultReceipt applyInTransaction(AgentResultProducerRegistry.Registration registration, String eventId) {
        var initial = inbox.find(registration, eventId, false);
        if (initial.eventV2() != null) return applyTyped(registration, initial);
        var dispatch = inbox.lockDispatch(registration, initial);
        var current = inbox.find(registration, eventId, true);
        if (current.receipt().state() != AgentResultReceiptStateEnum.RECEIVED) return current.receipt();
        if (!current.retryNotAfter().isAfter(inbox.databaseNow())) return inbox.finish(registration, current, AgentResultReceiptStateEnum.DEAD, "RETRY_DEADLINE");
        if (current.failures() >= maxFailures) return inbox.finish(registration, current, AgentResultReceiptStateEnum.DEAD, "RETRY_EXHAUSTED");
        AgentTerminalEvent event = current.event();
        if (!dispatch.query().equals(new LinkedAgentLookupQuery(event.idempotencyKey(), event.link()))
                || !dispatch.seed().binding().definitionKey().equals(event.definitionKey())
                || dispatch.seed().binding().definitionVersion() != event.definitionVersion()
                || dispatch.runId() != null && !dispatch.runId().equals(event.runId())) {
            return inbox.finish(registration, current, AgentResultReceiptStateEnum.DEAD, "DISPATCH_MISMATCH");
        }
        if (dispatch.state() == AgentDispatchStore.State.CLOSED || dispatch.state() == AgentDispatchStore.State.CLOSING) {
            return inbox.finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "DISPATCH_CLOSED");
        }
        var observed = workers.inspectAgentDispatch(dispatch);
        boolean active = observed.state() != FlowableAgentWaitInspector.State.PROCESS_ENDED && observed.state() != FlowableAgentWaitInspector.State.TIMER_WON;
        dispatch = inbox.refreshDispatch(dispatch, active);
        if (!active || dispatch.state() == AgentDispatchStore.State.CLOSED || dispatch.state() == AgentDispatchStore.State.CLOSING) {
            return inbox.finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "WAIT_ENDED");
        }
        if (observed.state() == FlowableAgentWaitInspector.State.MESSAGE_CONSUMED) {
            return inbox.finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "MESSAGE_CONSUMED");
        }
        if (dispatch.state() != AgentDispatchStore.State.ACKNOWLEDGED || dispatch.runId() == null
                || observed.state() != FlowableAgentWaitInspector.State.WAITING) {
            return inbox.reschedule(registration, current, retryDelay, maxFailures, false);
        }
        if (observed.modelWait() == null || observed.executionId() == null || observed.executionId().isBlank()) {
            throw new IllegalStateException("Agent result wait has no exact native execution");
        }
        var kind = ProcessCommandReceiptStore.Kind.AGENT_RESULT;
        receipts.insert(current.producer(), kind, eventId, event.fingerprint(), dispatch.seed().releaseKey(), dispatch.seed().correlationId());
        var audit = receipts.lock(current.producer(), kind, eventId);
        if (!event.fingerprint().equals(audit.requestHash()) || !dispatch.seed().releaseKey().equals(audit.releaseKey())
                || !dispatch.seed().correlationId().equals(audit.correlationId()) || !"PENDING".equals(audit.state()) || audit.resourceId() != null) {
            throw new IllegalStateException("Agent result application audit is inconsistent");
        }
        String target = observed.modelWait().statusVariable();
        Map<String, Object> variables = target == null ? Map.of() : Map.of(target, event.status().name());
        process.getRuntimeService().messageEventReceived(observed.modelWait().messageName(), observed.executionId(), variables);
        receipts.apply(current.producer(), kind, eventId, observed.executionId(), null);
        return inbox.finish(registration, current, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED");
    }

    private AgentResultReceipt applyTyped(AgentResultProducerRegistry.Registration registration, AgentResultInbox.Entry initial) {
        if (initial.receipt().state() != AgentResultReceiptStateEnum.RECEIVED) return initial.receipt();
        var participants = requireTyped();
        var event = initial.eventV2();
        var root = participants.guard().lockKnownRoot(event.tenantId(), event.link().scopeInstanceId());
        var release = participants.releases().find(root.getTenantId(), root.getReleaseKey()).orElseThrow(ProcessConflictException::new);
        var verified = participants.releaseService().verifiedAutomation(release);
        var node = participants.nodes().find(root.getTenantId(), root.getInstanceId(), event.nodeExecutionRef())
                .orElseThrow(ProcessConflictException::new);
        var observed = participants.inspector().inspectTyped(root, release, verified, node, event);
        var leases = observed.state() == FlowableAgentWaitInspector.State.WAITING
                ? lockAcknowledgedLeaseHistory(root.getTenantId(), node.getNativeJobId()) : List.<WorkerLeaseStore.RecoveryLeaseEvidence>of();
        var dispatch = inbox.lockDispatch(registration, initial);
        var current = inbox.find(registration, initial.eventId(), true);
        if (!event.equals(current.eventV2()) || !initial.producer().equals(current.producer())) throw new ProcessConflictException();
        if (current.receipt().state() != AgentResultReceiptStateEnum.RECEIVED) return current.receipt();
        var now = inbox.databaseNow();
        if (!current.retryNotAfter().isAfter(now)) return finish(registration, current, AgentResultReceiptStateEnum.DEAD, "RETRY_DEADLINE");
        if (current.failures() >= maxFailures) return finish(registration, current, AgentResultReceiptStateEnum.DEAD, "RETRY_EXHAUSTED");
        if (dispatch.state() == AgentDispatchStore.State.CLOSED || dispatch.state() == AgentDispatchStore.State.CLOSING) {
            return finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "DISPATCH_CLOSED");
        }
        var state = observed.state();
        if (state == FlowableAgentWaitInspector.State.PROCESS_ENDED || state == FlowableAgentWaitInspector.State.TIMER_WON
                || !now.isBefore(node.getNodeDeadlineAt()) || !now.isBefore(node.getAttemptDeadlineAt())) {
            return finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "WAIT_ENDED");
        }
        if (state == FlowableAgentWaitInspector.State.MESSAGE_CONSUMED) {
            return finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "MESSAGE_CONSUMED");
        }
        boolean acknowledged = FlowableAgentWaitInspector.validateTypedDispatch(dispatch, root, verified, node, event);
        if (!acknowledged || state != FlowableAgentWaitInspector.State.WAITING) {
            return inbox.reschedule(registration, current, retryDelay, maxFailures, false);
        }
        requireAcknowledgedLease(dispatch, leases);
        var exact = observed.waitEvidence();
        if (exact == null || !Objects.equals(dispatch.seed().businessKey(), exact.nativeBusinessKey())) throw new ProcessConflictException();
        Map<String, Object> result;
        try {
            result = normalizeResult(event, exact.nodeBinding().outputSchema());
        } catch (IllegalArgumentException invalid) {
            return finish(registration, current, AgentResultReceiptStateEnum.DEAD, "RESULT_SCHEMA_MISMATCH");
        }
        var subscription = exact.subscription();
        if (!participants.nodes().acceptAgentResult(node, subscription.getId(), event, result)) {
            return finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "WAIT_ENDED");
        }
        var kind = ProcessCommandReceiptStore.Kind.AGENT_RESULT;
        receipts.insert(current.producer(), kind, current.eventId(), current.fingerprint(), dispatch.seed().releaseKey(),
                dispatch.seed().correlationId());
        var audit = receipts.lock(current.producer(), kind, current.eventId());
        if (!current.fingerprint().equals(audit.requestHash()) || !dispatch.seed().releaseKey().equals(audit.releaseKey())
                || !dispatch.seed().correlationId().equals(audit.correlationId()) || !"PENDING".equals(audit.state()) || audit.resourceId() != null) {
            throw new IllegalStateException("Agent result application audit is inconsistent");
        }
        process.getRuntimeService().messageEventReceived(subscription.getEventName(), subscription.getExecutionId());
        receipts.apply(current.producer(), kind, current.eventId(), subscription.getExecutionId(), null);
        var closed = inbox.refreshDispatch(dispatch, false);
        if (closed.state() != AgentDispatchStore.State.CLOSING && closed.state() != AgentDispatchStore.State.CLOSED) {
            throw new IllegalStateException("Consumed Agent result did not schedule dispatch closure");
        }
        return finish(registration, current, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED");
    }

    private List<WorkerLeaseStore.RecoveryLeaseEvidence> lockAcknowledgedLeaseHistory(long tenantId, String jobId) {
        String schema = process.getDatabaseSchema();
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}") || jobId == null) throw new ProcessConflictException();
        jdbc.queryForList("SELECT token_sha256 FROM " + schema + ".tianji_worker_lease_receipt"
                + " WHERE tenant_id=? AND job_id=? AND state='COMPLETED' ORDER BY token_sha256 LIMIT 2 FOR SHARE",
                String.class, tenantId, jobId);
        return WorkerLeaseStore.readRecoveryHistory(jdbc, schema, tenantId, jobId);
    }

    private static void requireAcknowledgedLease(AgentDispatchStore.Intent dispatch,
            List<WorkerLeaseStore.RecoveryLeaseEvidence> leases) {
        var seed = dispatch.seed();
        var worker = seed.binding();
        var actor = worker.actor();
        var completed = leases.stream().filter(lease -> "COMPLETED".equals(lease.state())).toList();
        if (completed.size() != 1) throw new ProcessConflictException();
        var lease = completed.getFirst();
        String expectedResult = ProcessRequestDigest.hash(List.of("worker-complete-v1", seed.occurrenceId(),
                seed.occurrenceId(), "agent-" + dispatch.runId()), Map.of("runId", dispatch.runId()));
        if (lease.tenantId() != actor.tenantId() || !actor.subjectId().equals(lease.subjectId())
                || !actor.issuerFingerprint().equals(lease.subjectIssuer()) || !worker.topic().equals(lease.topic())
                || !worker.schemaVersion().equals(lease.schemaVersion()) || !seed.occurrenceId().equals(lease.jobId())
                || !seed.occurrenceId().equals(lease.occurrenceId()) || !"BPMN".equals(lease.scopeType())
                || !seed.instanceId().equals(lease.scopeId()) || !seed.releaseKey().equals(lease.releaseKey())
                || !seed.processDefinitionId().equals(lease.definitionId()) || !seed.elementId().equals(lease.elementId())
                || !seed.processModelSha256().equals(lease.modelSha256()) || !seed.workerInputSha256().equals(lease.inputSha256())
                || lease.decidedAt() == null || !lease.hasBusinessReceipt() || !expectedResult.equals(lease.resultSha256())) {
            throw new ProcessConflictException();
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalizeResult(AgentTerminalEventV2 event, ProcessSchemaVO schema) {
        if (event.status() != AgentRunStatusEnum.COMPLETED) return null;
        boolean artifact = "object".equals(schema.type()) && Boolean.FALSE.equals(schema.additionalProperties())
                && ARTIFACT_SCHEMA.properties().equals(schema.properties())
                && Set.copyOf(ARTIFACT_SCHEMA.required()).equals(Set.copyOf(schema.required()));
        if (artifact != (event.artifactRef() != null)) throw invalid();
        var wire = artifact ? event.artifactRef().toWire() : event.output();
        var value = schema.normalizeValue(wire);
        if (!(value instanceof Map<?, ?> normalized)
                || !Arrays.equals(ProcessTypedJson.canonicalBytes(normalized), ProcessTypedJson.canonicalBytes(wire))) throw invalid();
        return (Map<String, Object>) normalized;
    }

    private AgentResultReceipt finish(AgentResultProducerRegistry.Registration registration, AgentResultInbox.Entry entry,
            AgentResultReceiptStateEnum state, String code) {
        return inbox.finish(registration, entry, state, code);
    }

    private TypedParticipantsVO requireTyped() {
        if (typed == null) throw new IllegalStateException("Typed Agent result application is unavailable");
        return typed;
    }

    public Reconciliation reconcile(AgentResultProducerRegistry.Registration registration, int limit) {
        requireOwnTransaction();
        int applied = 0;
        int ignored = 0;
        int pending = 0;
        int dead = 0;
        int errors = 0;
        var candidates = inbox.due(registration, limit, retryDelay);
        for (var candidate : candidates) {
            try {
                var receipt = apply(registration, candidate.receipt().eventId());
                switch (receipt.state()) {
                    case APPLIED -> applied++;
                    case IGNORED_LATE -> ignored++;
                    case DEAD -> dead++;
                    case RECEIVED -> pending++;
                }
            } catch (RuntimeException failure) {
                log.warn("Agent result application failed: type={}", failure.getClass().getName());
                errors++;
            }
        }
        return new Reconciliation(candidates.size(), applied, ignored, pending, dead, errors);
    }

    private static void requireOwnTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Agent result application must own its Process transaction");
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent result application configuration is invalid"); }
    public record Reconciliation(int observed, int applied, int ignoredLate, int pending, int dead, int errors) { }

    public record TypedParticipantsVO(AutomationInstanceGuardCommandInterceptor guard, AutomationReleaseStore releases,
            ProcessReleaseService releaseService, ProcessNodeExecutionStore nodes, FlowableAgentWaitInspector inspector) {
        public TypedParticipantsVO {
            Objects.requireNonNull(guard);
            Objects.requireNonNull(releases);
            Objects.requireNonNull(releaseService);
            Objects.requireNonNull(nodes);
            Objects.requireNonNull(inspector);
        }
    }
}
