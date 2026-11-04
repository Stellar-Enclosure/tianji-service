package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.cmmn.api.CmmnHistoryService;
import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemDefinitionType;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.CaseNativeAccess;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedCaseModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox.*;

/** Owns short local transactions for positive Case completion proof and exact native Message delivery. */
public final class CaseCompletionCorrelationService {
    private static final Logger log = LoggerFactory.getLogger(CaseCompletionCorrelationService.class);
    private final DataSource source;
    private final SpringProcessEngineConfiguration process;
    private final CmmnHistoryService history;
    private final ProcessReleaseService releases;
    private final VerifiedProcessModels models;
    private final CaseStartProvenanceStore provenance;
    private final CaseCompletionInbox inbox;
    private final CaseSourceRegistry registry;
    private final FlowableCaseWaitInspector waits;
    private final CaseNativeAccess cases;
    private final ProcessCommandReceiptStore commands;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Duration retryDelay;
    private final int maxFailures;
    private final String executions;
    private final String subscriptions;

    public CaseCompletionCorrelationService(SpringProcessEngineConfiguration process, SpringCmmnEngineConfiguration cmmn,
            ProcessReleaseService releases, VerifiedProcessModels models, CaseStartProvenanceStore provenance, CaseCompletionInbox inbox,
            CaseSourceRegistry registry, FlowableCaseWaitInspector waits, Duration retryDelay, int maxFailures) {
        this.process = Objects.requireNonNull(process, "process"); Objects.requireNonNull(cmmn, "cmmn");
        this.releases = Objects.requireNonNull(releases, "releases"); this.models = Objects.requireNonNull(models, "models");
        this.provenance = Objects.requireNonNull(provenance, "provenance"); this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.registry = Objects.requireNonNull(registry, "registry"); this.waits = Objects.requireNonNull(waits, "waits");
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null || process.getRuntimeService() == null
                || retryDelay == null || retryDelay.compareTo(Duration.ofMillis(100)) < 0 || retryDelay.compareTo(Duration.ofHours(1)) > 0 || retryDelay.toNanos() % 1_000_000 != 0 || maxFailures < 1 || maxFailures > 1000) throw invalid();
        source = manager.getDataSource(); String schema = process.getDatabaseSchema();
        var processSource = process.getDataSource(); if (processSource instanceof TransactionAwareDataSourceProxy proxy) processSource = proxy.getTargetDataSource();
        var caseSource = cmmn.getDataSource(); if (caseSource instanceof TransactionAwareDataSourceProxy proxy) caseSource = proxy.getTargetDataSource();
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}") || processSource != source || caseSource != source || cmmn.getTransactionManager() != manager
                || !schema.equals(cmmn.getDatabaseSchema()) || !provenance.usesDataSource(source) || cmmn.getCmmnHistoryService() == null || !cmmn.isDbHistoryUsed()
                || cmmn.getHistoryLevel() == null || !cmmn.getHistoryLevel().isAtLeast(HistoryLevel.AUDIT) || cmmn.isAsyncHistoryEnabled()) throw invalid();
        FlowableAgentWaitInspector.requireHistory(process); inbox.requireStorage(source, manager, schema);
        history = cmmn.getCmmnHistoryService(); cases = new CaseNativeAccess(source, cmmn);
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); commands = new ProcessCommandReceiptStore(jdbc, schema);
        transaction = new TransactionTemplate(manager); transaction.setTimeout(10); this.retryDelay = retryDelay; this.maxFailures = maxFailures;
        executions = '"' + schema + "\".act_ru_execution"; subscriptions = '"' + schema + "\".act_ru_event_subscr";
    }

    public Entry apply(CaseSourceRegistry.Registration registration, String completionId) {
        ownTransaction(); Objects.requireNonNull(registration, "registration"); var key = new DueKey(registration.actor().tenantId(), completionId);
        try { return Objects.requireNonNull(transaction.execute(status -> applyInTransaction(registration, key))); }
        catch (ForeignNamespace foreignNamespace) { throw new ProcessResourceNotFoundException(); }
        catch (RuntimeException failure) {
            try { transaction.executeWithoutResult(status -> retry(registration, key)); }
            catch (RuntimeException unavailable) { log.warn("Case completion retry persistence failed: type={}", unavailable.getClass().getName()); }
            throw failure;
        }
    }

    private Entry applyInTransaction(CaseSourceRegistry.Registration registration, DueKey key) {
        var current = lock(key, registration); var origin = origin(current);
        if (current.state() != State.PENDING) { requireCurrent(registration, origin); return current; }
        if (!available(registration, origin)) return inbox.finish(current, State.DEAD, DecisionCode.SOURCE_UNAVAILABLE);
        FlowableCaseWaitInspector.Observation observed;
        try { observed = waits.inspect(origin); }
        catch (ProcessConflictException inconsistent) { return inbox.finish(current, State.DEAD, DecisionCode.INCONSISTENT_NATIVE_FACT); }
        if (late(observed)) return finishCurrent(registration, origin, current, State.IGNORED_LATE,
                observed.state() == FlowableCaseWaitInspector.State.MESSAGE_CONSUMED ? DecisionCode.MESSAGE_CONSUMED : DecisionCode.WAIT_ENDED);
        var child = cases.observe(origin); var seed = origin.seed();
        if (!seed.caseInstanceId().equals(child.caseId()) || !seed.caseEngineDefinitionId().equals(child.definitionId()) || !Long.toString(seed.owner().tenantId()).equals(child.tenantId()) || !seed.businessKey().equals(child.businessKey())) throw unavailable();
        if ("active".equals(child.state())) {
            requireCurrent(registration, origin); var result = inbox.reschedule(current, retryDelay); requireCurrent(registration, origin); return result;
        }
        TerminalFacts facts;
        try { facts = terminalFacts(registration, origin, child); }
        catch (InconsistentFacts inconsistent) { return finishCurrent(registration, origin, current, State.DEAD, DecisionCode.INCONSISTENT_NATIVE_FACT); }
        if (facts.status() == TerminalStatus.TERMINATED) {
            if (current.terminalFacts() != null && !current.terminalFacts().equals(facts)) return finishCurrent(registration, origin, current, State.DEAD, DecisionCode.INCONSISTENT_NATIVE_FACT);
            requireCurrent(registration, origin); var result = inbox.finish(current, State.DEAD, DecisionCode.CASE_TERMINATED, facts); requireCurrent(registration, origin); return result;
        }
        if (current.terminalFacts() != null && !current.terminalFacts().equals(facts)) return finishCurrent(registration, origin, current, State.DEAD, DecisionCode.INCONSISTENT_NATIVE_FACT);
        if (observed.state() != FlowableCaseWaitInspector.State.WAITING) {
            requireCurrent(registration, origin); var result = inbox.reschedule(current, facts, retryDelay); requireCurrent(registration, origin); return result;
        }
        String hash = requestHash(origin, facts, observed); var actor = registration.actor(); var kind = ProcessCommandReceiptStore.Kind.CASE_COMPLETION_SERVICE;
        commands.insert(actor, kind, key.completionCommandId(), hash, seed.releaseKey(), seed.correlationId());
        var audit = commands.lock(actor, kind, key.completionCommandId());
        if (!hash.equals(audit.requestHash()) || !seed.releaseKey().equals(audit.releaseKey()) || !seed.correlationId().equals(audit.correlationId()) || !"PENDING".equals(audit.state()) || audit.resourceId() != null) throw unavailable();
        fence(origin, observed);
        var fresh = waits.inspect(origin); if (!observed.equals(fresh) || fresh.state() != FlowableCaseWaitInspector.State.WAITING) throw new ProcessConflictException();
        requireCurrent(registration, origin);
        process.getRuntimeService().messageEventReceived(fresh.messageName(), fresh.executionId(), Map.of());
        requireCurrent(registration, origin); commands.apply(actor, kind, key.completionCommandId(), fresh.executionId(), null);
        var result = inbox.finish(current, State.APPLIED, DecisionCode.MESSAGE_APPLIED, facts, new AppliedCommand(hash, fresh.executionId()));
        requireCurrent(registration, origin); return result;
    }

    /** Discovery is not authority; lock CaseScope before the mutable Inbox and reread its identity. */
    private Entry lock(DueKey key, CaseSourceRegistry.Registration registration) {
        current(); var found = inbox.find(key);
        if (found.isEmpty()) {
            if (registration != null) throw new ForeignNamespace();
            throw new ProcessResourceNotFoundException();
        }
        var initial = found.orElseThrow();
        if (registration != null && !initial.registration().equals(registration.inboxKey())) throw new ForeignNamespace();
        provenance.lockScope(key.tenantId(), initial.caseInstanceId()); var locked = inbox.lock(key);
        if (!key.equals(locked.key()) || !initial.caseInstanceId().equals(locked.caseInstanceId()) || !initial.registration().equals(locked.registration())) throw unavailable();
        if (registration != null && !locked.registration().equals(registration.inboxKey())) throw new ForeignNamespace();
        current(); return locked;
    }
    private CaseStartProvenanceStore.Entry origin(Entry entry) {
        var origin = provenance.find(entry.key().tenantId(), entry.caseInstanceId()).orElseThrow(CaseCompletionCorrelationService::unavailable); var seed = origin.seed();
        if (!"LINKED_BPMN".equals(seed.startKind()) || !seed.caseInstanceId().equals(entry.caseInstanceId()) || !entry.registration().equals(new RegistrationKey(seed.executor().tenantId(), seed.executor().issuerFingerprint(), seed.executor().subjectId(), seed.sourceId()))) throw unavailable();
        return origin;
    }
    private boolean available(CaseSourceRegistry.Registration registration, CaseStartProvenanceStore.Entry origin) {
        var seed = origin.seed(); var binding = registration.binding();
        return registration.actor().equals(seed.executor()) && registration.sourceId().equals(seed.sourceId()) && binding.tenantId() == seed.owner().tenantId()
                && binding.releaseKey().equals(seed.releaseKey()) && binding.parentDefinitionKey().equals(seed.parentDefinitionKey()) && binding.parentModelSha256().equals(seed.parentModelSha256())
                && binding.sourceElementId().equals(seed.sourceElementId()) && binding.caseDefinitionKey().equals(seed.caseDefinitionKey()) && binding.caseModelSha256().equals(seed.caseModelSha256()) && registry.current(registration);
    }
    private void requireCurrent(CaseSourceRegistry.Registration registration, CaseStartProvenanceStore.Entry origin) { if (!available(registration, origin)) throw unavailable(); current(); }
    private Entry finishCurrent(CaseSourceRegistry.Registration registration, CaseStartProvenanceStore.Entry origin, Entry current, State state, DecisionCode code) {
        requireCurrent(registration, origin); var result = inbox.finish(current, state, code); requireCurrent(registration, origin); return result;
    }
    private void retry(CaseSourceRegistry.Registration registration, DueKey key) {
        var current = lock(key, registration); if (current.state() != State.PENDING) return; var origin = origin(current);
        boolean available;
        try { available = available(registration, origin); }
        catch (RuntimeException localMetadataUnavailable) {
            // Ownership is already verified; an unknown local lookup failure consumes the technical budget.
            inbox.rescheduleFailure(current, retryDelay, maxFailures); return;
        }
        if (!available) inbox.finish(current, State.DEAD, DecisionCode.SOURCE_UNAVAILABLE);
        else inbox.rescheduleFailure(current, retryDelay, maxFailures);
    }

    public Entry applyOrphan(DueKey key) {
        ownTransaction(); Objects.requireNonNull(key, "key");
        return Objects.requireNonNull(transaction.execute(status -> {
            var current = lock(key, null); if (current.state() != State.PENDING) return current; var origin = origin(current);
            var configured = registry.find(current.registration());
            if (configured.isPresent() && available(configured.orElseThrow(), origin)) return inbox.reschedule(current, retryDelay);
            return inbox.finish(current, State.DEAD, DecisionCode.SOURCE_UNAVAILABLE);
        }));
    }

    private TerminalFacts terminalFacts(CaseSourceRegistry.Registration registration, CaseStartProvenanceStore.Entry origin, CaseNativeAccess.Observation observed) {
        var seed = origin.seed(); String tenant = Long.toString(seed.owner().tenantId());
        var ended = history.createHistoricCaseInstanceQuery().caseInstanceId(seed.caseInstanceId()).caseDefinitionId(seed.caseEngineDefinitionId()).caseInstanceTenantId(tenant).singleResult();
        if (ended == null || ended.getEndTime() == null || !seed.caseInstanceId().equals(ended.getId()) || !seed.caseEngineDefinitionId().equals(ended.getCaseDefinitionId()) || !tenant.equals(ended.getTenantId())
                || !seed.businessKey().equals(ended.getBusinessKey()) || !observed.state().equals(ended.getState()) || observed.finishedAt() == null || !observed.finishedAt().equals(ended.getEndTime().toInstant())) throw inconsistent();
        var fields = sourceFields(origin); fields.add("TIANJI_CASE_TERMINAL_FACT_V1"); fields.add(ended.getId()); fields.add(ended.getState()); fields.add(ended.getEndTime().toInstant().toString());
        if ("terminated".equals(ended.getState())) return new TerminalFacts(TerminalStatus.TERMINATED, ProcessRequestDigest.hash(fields, Map.of()), ended.getEndTime().toInstant());
        if (!"completed".equals(ended.getState())) throw inconsistent();
        var model = caseModel(registration, origin);
        var items = history.createHistoricPlanItemInstanceQuery().planItemInstanceCaseInstanceId(seed.caseInstanceId()).planItemInstanceCaseDefinitionId(seed.caseEngineDefinitionId()).planItemInstanceTenantId(tenant).listPage(0, 4);
        if (items == null || items.size() != 3) throw inconsistent(); var byElement = new HashMap<String, HistoricPlanItemInstance>(); var ids = new HashSet<String>();
        for (var item : items) {
            if (item == null || item.getId() == null || item.getId().isBlank() || !ids.add(item.getId()) || !seed.caseInstanceId().equals(item.getCaseInstanceId()) || !seed.caseEngineDefinitionId().equals(item.getCaseDefinitionId()) || !tenant.equals(item.getTenantId())
                    || byElement.put(item.getElementId(), item) != null) throw inconsistent();
        }
        if (!byElement.keySet().equals(Set.of("primary", "supplemental", "milestone"))) throw inconsistent();
        for (String element : List.of("primary", "supplemental", "milestone")) {
            var item = byElement.get(element); var policy = model.planItems().get(element);
            String kind = "milestone".equals(element) ? PlanItemDefinitionType.MILESTONE : PlanItemDefinitionType.HUMAN_TASK;
            if (policy == null || !policy.definitionId().equals(item.getPlanItemDefinitionId()) || !kind.equals(item.getPlanItemDefinitionType()) || item.getEndedTime() == null) throw inconsistent();
            if ("primary".equals(element) && (!"completed".equals(item.getState()) || item.getCompletedTime() == null)
                    || "supplemental".equals(element) && !("completed".equals(item.getState()) && item.getCompletedTime() != null || "terminated".equals(item.getState()) && item.getTerminatedTime() != null)
                    || "milestone".equals(element) && (!"completed".equals(item.getState()) || item.getOccurredTime() == null)) throw inconsistent();
            fields.addAll(List.of(item.getId(), element, item.getPlanItemDefinitionId(), kind, item.getState(), date(item.getCompletedTime()), date(item.getOccurredTime()), date(item.getTerminatedTime()), item.getEndedTime().toInstant().toString()));
        }
        var milestones = history.createHistoricMilestoneInstanceQuery().milestoneInstanceCaseInstanceId(seed.caseInstanceId()).milestoneInstanceCaseDefinitionId(seed.caseEngineDefinitionId()).milestoneInstanceTenantId(tenant).listPage(0, 2);
        if (milestones == null || milestones.size() != 1) throw inconsistent(); var milestone = milestones.getFirst();
        if (milestone == null || milestone.getId() == null || milestone.getId().isBlank() || !seed.caseInstanceId().equals(milestone.getCaseInstanceId()) || !seed.caseEngineDefinitionId().equals(milestone.getCaseDefinitionId()) || !tenant.equals(milestone.getTenantId()) || !"milestone".equals(milestone.getElementId()) || milestone.getTimeStamp() == null) throw inconsistent();
        fields.addAll(List.of(milestone.getId(), milestone.getElementId(), milestone.getTimeStamp().toInstant().toString()));
        return new TerminalFacts(TerminalStatus.COMPLETED, ProcessRequestDigest.hash(fields, Map.of()), ended.getEndTime().toInstant());
    }
    private ValidatedCaseModel caseModel(CaseSourceRegistry.Registration registration, CaseStartProvenanceStore.Entry origin) {
        var seed = origin.seed(); var release = releases.get(registration.actor(), seed.releaseKey());
        if (!seed.releaseKey().equals(release.releaseKey())) throw inconsistent();
        var definitions = release.definitions().stream().filter(value -> value.type() == ValidatedProcessModel.Type.CMMN && seed.caseEngineDefinitionId().equals(value.engineDefinitionId())).toList();
        if (definitions.size() != 1) throw inconsistent(); var definition = definitions.getFirst();
        if (!seed.caseDefinitionKey().equals(definition.definitionKey()) || !seed.caseModelSha256().equals(definition.modelSha256())) throw inconsistent();
        var model = models.caseModel(seed.owner().tenantId(), definition); if (!seed.caseDefinitionKey().equals(model.definitionKey()) || model.planItems().size() != 3) throw inconsistent(); return model;
    }

    private void fence(CaseStartProvenanceStore.Entry origin, FlowableCaseWaitInspector.Observation observed) {
        var seed = origin.seed(); current();
        var rows = jdbc.query("SELECT e.id_ FROM " + executions + " e JOIN " + subscriptions + " s ON s.execution_id_=e.id_ AND s.tenant_id_=e.tenant_id_ AND s.proc_inst_id_=e.proc_inst_id_ AND s.proc_def_id_=e.proc_def_id_ AND s.activity_id_=e.act_id_ "
                        + "WHERE e.id_=? AND e.tenant_id_=? AND e.proc_inst_id_=? AND e.proc_def_id_=? AND e.act_id_=? AND s.event_type_='message' AND s.event_name_=? LIMIT 2 FOR UPDATE OF e,s",
                (rs, row) -> rs.getString(1), observed.executionId(), Long.toString(seed.owner().tenantId()), seed.parentProcessInstanceId(), seed.parentEngineDefinitionId(), observed.waitActivityId(), observed.messageName());
        if (rows.size() != 1 || !observed.executionId().equals(rows.getFirst())) throw new ProcessConflictException(); current();
    }
    private static String requestHash(CaseStartProvenanceStore.Entry origin, TerminalFacts facts, FlowableCaseWaitInspector.Observation wait) {
        var fields = sourceFields(origin); fields.addAll(List.of("TIANJI_CASE_COMPLETION_REQUEST_V1", facts.status().name(), facts.sha256(), facts.terminalAt().toString(), wait.sourceElementId(), wait.waitActivityId(), wait.messageName(), wait.timerActivityId(), wait.executionId()));
        return ProcessRequestDigest.hash(fields, Map.of());
    }
    private static ArrayList<String> sourceFields(CaseStartProvenanceStore.Entry origin) {
        var p = origin.seed(); return new ArrayList<>(List.of(Long.toString(p.owner().tenantId()), p.caseInstanceId(), p.releaseKey(), p.caseDefinitionKey(), p.caseEngineDefinitionId(), p.caseModelSha256(),
                p.owner().subjectType().name(), p.owner().issuerFingerprint(), p.owner().subjectId(), p.ownerStartCommandType(), p.ownerStartCommandId(), p.ownerStartRequestSha256(),
                p.executor().subjectType().name(), p.executor().issuerFingerprint(), p.executor().subjectId(), p.caseStartCommandId(), p.caseStartRequestSha256(), p.sourceId(), p.parentProcessInstanceId(), p.sourceOccurrenceId(), p.sourceElementId(), p.parentDefinitionKey(), p.parentEngineDefinitionId(), p.parentModelSha256(), p.businessKey(), p.correlationId(), origin.createdAt().toString()));
    }
    private static String date(java.util.Date value) { return value == null ? "" : value.toInstant().toString(); }
    private static boolean late(FlowableCaseWaitInspector.Observation observed) { return observed.state() == FlowableCaseWaitInspector.State.PROCESS_ENDED || observed.state() == FlowableCaseWaitInspector.State.TIMER_WON || observed.state() == FlowableCaseWaitInspector.State.MESSAGE_CONSUMED; }

    public Reconciliation reconcile(CaseSourceRegistry.Registration registration, int limit) {
        ownTransaction(); Objects.requireNonNull(registration, "registration"); bounds(limit); var counts = new int[6]; var seen = new HashSet<DueKey>(); int remaining = limit;
        while (remaining > 0) {
            int chunk = Math.min(remaining, 32); var candidates = inbox.due(registration.inboxKey(), chunk, retryDelay); if (candidates.size() > chunk) throw unavailable(); remaining -= candidates.size();
            for (var key : candidates) if (seen.add(key)) { counts[0]++; try { count(counts, apply(registration, key.completionCommandId())); } catch (RuntimeException failure) { counts[5]++; log.warn("Case completion application failed: type={}", failure.getClass().getName()); } }
            if (candidates.size() < chunk) break;
        }
        return summary(counts);
    }
    public Reconciliation reconcileOrphans(Set<RegistrationKey> active, int limit) {
        ownTransaction(); bounds(limit); if (active == null || active.size() > 256) throw invalid(); var snapshot = Set.copyOf(active); var counts = new int[6]; var seen = new HashSet<DueKey>(); int remaining = limit;
        while (remaining > 0) {
            int chunk = Math.min(remaining, 32); var candidates = inbox.orphanDue(snapshot, chunk, retryDelay); if (candidates.size() > chunk) throw unavailable(); remaining -= candidates.size();
            for (var key : candidates) if (seen.add(key)) { counts[0]++; try { count(counts, applyOrphan(key)); } catch (RuntimeException failure) { counts[5]++; log.warn("Case orphan application failed: type={}", failure.getClass().getName()); } }
            if (candidates.size() < chunk) break;
        }
        return summary(counts);
    }
    private static void count(int[] counts, Entry result) { switch (result.state()) { case APPLIED -> counts[1]++; case IGNORED_LATE -> counts[2]++; case PENDING -> counts[3]++; case DEAD -> counts[4]++; } }
    private static Reconciliation summary(int[] counts) { return new Reconciliation(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5]); }
    private void current() {
        if (Thread.currentThread().isInterrupted() || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
    }
    private static void ownTransaction() { if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Case completion must own its short Process transaction"); }
    private static void bounds(int limit) { if (limit < 1 || limit > 100) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case completion configuration is invalid"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Case completion source or native wait is unavailable"); }
    private static InconsistentFacts inconsistent() { return new InconsistentFacts(); }
    /** Only an absent intent or an actual tuple mismatch exempts failure compensation. */
    private static final class ForeignNamespace extends RuntimeException { private static final long serialVersionUID = 1L; }
    private static final class InconsistentFacts extends RuntimeException { private static final long serialVersionUID = 1L; }
    public record Reconciliation(int observed, int applied, int ignoredLate, int pending, int dead, int errors) { }
}
