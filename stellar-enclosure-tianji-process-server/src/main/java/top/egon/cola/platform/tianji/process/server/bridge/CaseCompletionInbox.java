package top.egon.cola.platform.tianji.process.server.bridge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Durable linked-Case completion participant; native Case state remains owned by Flowable. */
public final class CaseCompletionInbox {
    private final DataSource source;
    private final DataSourceTransactionManager manager;
    private final String schema;
    private final String table;
    private final String commandsTable;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final CaseStartProvenanceStore provenance;

    public CaseCompletionInbox(DataSource source, DataSourceTransactionManager manager, String schema) {
        if (source == null || manager == null || manager.getDataSource() != source || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        this.source = source; this.manager = manager; this.schema = schema;
        table = '"' + schema + "\".tianji_case_completion_inbox"; commandsTable = '"' + schema + "\".tianji_process_command_receipt";
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        transaction = new TransactionTemplate(manager); transaction.setTimeout(10);
        provenance = new CaseStartProvenanceStore(source, schema); provenance.verifyStorage();
    }

    public Entry prepare(CaseStartProvenanceStore.Entry proposed) {
        requireTransaction();
        if (proposed == null || !"LINKED_BPMN".equals(proposed.seed().startKind()) || proposed.seed().executor().subjectType() != ProcessActor.SubjectType.SERVICE) throw invalid();
        var seed = proposed.seed(); var persisted = origin(seed.executor().tenantId(), seed.caseInstanceId());
        if (!persisted.equals(proposed)) throw unavailable();
        var key = new DueKey(seed.executor().tenantId(), completionId(seed.executor().tenantId(), seed.caseInstanceId()));
        var registration = new RegistrationKey(key.tenantId(), seed.executor().issuerFingerprint(), seed.executor().subjectId(), seed.sourceId());
        Instant now = databaseNow();
        jdbc.update("INSERT INTO " + table + " (tenant_id,completion_command_id,case_instance_id,service_type,service_issuer,service_subject,source_id,state,"
                        + "terminal_status,terminal_sha256,terminal_at,created_at,next_attempt_at,failure_count,decided_at,decision_code,applied_command_type) "
                        + "VALUES (?,?,?,'SERVICE',?,?,?,'PENDING',NULL,NULL,NULL,?,?,0,NULL,NULL,NULL) ON CONFLICT DO NOTHING",
                key.tenantId(), key.completionCommandId(), seed.caseInstanceId(), registration.serviceIssuer(), registration.serviceSubject(), registration.sourceId(), Timestamp.from(now), Timestamp.from(now));
        var stored = find(key).orElseThrow(CaseCompletionInbox::unavailable);
        if (!stored.caseInstanceId().equals(seed.caseInstanceId()) || !stored.registration().equals(registration)) throw unavailable();
        return stored;
    }

    /** Unlocked discovery supplies a CaseScope key; it does not authorize application or replace the subsequent locked read. */
    public Optional<Entry> find(DueKey key) { return read(key, false); }

    /** Caller holds the shared CaseScope advisory lock before taking this mutable Inbox row lock. */
    public Entry lock(DueKey key) { return read(key, true).orElseThrow(CaseCompletionInbox::unavailable); }

    private Optional<Entry> read(DueKey key, boolean lock) {
        requireTransaction(); if (key == null) throw invalid();
        var rows = jdbc.query("SELECT * FROM " + table + " WHERE tenant_id=? AND completion_command_id=? LIMIT 2" + (lock ? " FOR UPDATE" : ""),
                (rs, number) -> entry(rs), key.tenantId(), key.completionCommandId());
        if (rows.size() > 1) throw unavailable();
        if (rows.isEmpty()) return Optional.empty();
        var stored = rows.getFirst(); if (!stored.key().equals(key)) throw unavailable();
        var source = origin(key.tenantId(), stored.caseInstanceId()); requireOrigin(stored, source);
        if (stored.state() == State.APPLIED) requireApplied(stored, source, null);
        return Optional.of(stored);
    }

    /** Short claims commit before the caller starts CaseScope application, avoiding Inbox-to-CaseScope lock inversion. */
    public List<DueKey> due(RegistrationKey registration, int limit, Duration claimInterval) {
        discovery(limit, claimInterval); if (registration == null) throw invalid();
        return transaction.execute(status -> {
            requireTransaction();
            return jdbc.query("WITH candidates AS (SELECT tenant_id,completion_command_id FROM " + table
                            + " WHERE tenant_id=? AND service_issuer=? AND service_subject=? AND source_id=? AND state='PENDING' AND next_attempt_at<=clock_timestamp() "
                            + "ORDER BY next_attempt_at,completion_command_id LIMIT ? FOR UPDATE SKIP LOCKED) UPDATE " + table
                            + " i SET next_attempt_at=clock_timestamp() + (? * interval '1 millisecond') FROM candidates c "
                            + "WHERE i.tenant_id=c.tenant_id AND i.completion_command_id=c.completion_command_id RETURNING i.tenant_id,i.completion_command_id",
                    (rs, number) -> new DueKey(rs.getLong("tenant_id"), rs.getString("completion_command_id")), registration.tenantId(), registration.serviceIssuer(),
                    registration.serviceSubject(), registration.sourceId(), limit, claimInterval.toMillis());
        });
    }

    /** Current source removal cannot hide old durable intents. This scan only discovers keys and grants no native authority. */
    public List<DueKey> orphanDue(Set<RegistrationKey> activeRegistrations, int limit, Duration claimInterval) {
        discovery(limit, claimInterval); if (activeRegistrations == null || activeRegistrations.size() > 256 || activeRegistrations.stream().anyMatch(value -> value == null)) throw invalid();
        var active = Set.copyOf(activeRegistrations).stream().sorted(Comparator.comparingLong(RegistrationKey::tenantId)
                .thenComparing(RegistrationKey::serviceIssuer).thenComparing(RegistrationKey::serviceSubject).thenComparing(RegistrationKey::sourceId)).toList();
        var parameters = new ArrayList<Object>(); var exclusion = new StringBuilder();
        if (!active.isEmpty()) {
            exclusion.append(" AND NOT EXISTS (SELECT 1 FROM (VALUES ");
            for (int i = 0; i < active.size(); i++) {
                if (i > 0) exclusion.append(',');
                exclusion.append("(CAST(? AS bigint),CAST(? AS varchar) COLLATE \"C\",CAST(? AS varchar) COLLATE \"C\",CAST(? AS varchar) COLLATE \"C\")");
                var registration = active.get(i); parameters.add(registration.tenantId()); parameters.add(registration.serviceIssuer()); parameters.add(registration.serviceSubject()); parameters.add(registration.sourceId());
            }
            exclusion.append(") AS active(tenant_id,service_issuer,service_subject,source_id) WHERE active.tenant_id=i.tenant_id AND active.service_issuer=i.service_issuer "
                    + "AND active.service_subject=i.service_subject AND active.source_id=i.source_id)");
        }
        parameters.add(limit); parameters.add(claimInterval.toMillis());
        return transaction.execute(status -> {
            requireTransaction();
            return jdbc.query("WITH candidates AS (SELECT tenant_id,completion_command_id FROM " + table
                            + " i WHERE state='PENDING' AND next_attempt_at<=clock_timestamp()" + exclusion
                            + " ORDER BY next_attempt_at,tenant_id,completion_command_id LIMIT ? FOR UPDATE SKIP LOCKED) UPDATE " + table
                            + " i SET next_attempt_at=clock_timestamp() + (? * interval '1 millisecond') FROM candidates c "
                            + "WHERE i.tenant_id=c.tenant_id AND i.completion_command_id=c.completion_command_id RETURNING i.tenant_id,i.completion_command_id",
                    (rs, number) -> new DueKey(rs.getLong("tenant_id"), rs.getString("completion_command_id")), parameters.toArray());
        });
    }

    public Entry reschedule(Entry expected, Duration delay) { return reschedule(expected, expected == null ? null : expected.terminalFacts(), delay); }

    public Entry reschedule(Entry expected, TerminalFacts facts, Duration delay) {
        delay(delay); if (facts != null && facts.status() != TerminalStatus.COMPLETED) throw invalid();
        var current = current(expected); var retained = retain(current, facts);
        return pending(current, retained, delay, current.failureCount());
    }

    public Entry rescheduleFailure(Entry expected, Duration delay, int maxFailures) {
        delay(delay); if (maxFailures < 1 || maxFailures > 1000) throw invalid();
        var current = current(expected); if (current.failureCount() == Integer.MAX_VALUE) throw unavailable();
        int failures = current.failureCount() + 1;
        return failures >= maxFailures ? decide(current, State.DEAD, DecisionCode.RETRY_EXHAUSTED, current.terminalFacts(), failures, null)
                : pending(current, current.terminalFacts(), delay, failures);
    }

    private Entry pending(Entry current, TerminalFacts facts, Duration delay, int failures) {
        if (jdbc.update("UPDATE " + table + " SET terminal_status=?,terminal_sha256=?,terminal_at=?,next_attempt_at=clock_timestamp() + (? * interval '1 millisecond'),failure_count=? "
                        + "WHERE tenant_id=? AND completion_command_id=? AND state='PENDING'", facts == null ? null : facts.status().name(), facts == null ? null : facts.sha256(),
                facts == null ? null : Timestamp.from(facts.terminalAt()), delay.toMillis(), failures, current.key().tenantId(), current.key().completionCommandId()) != 1) throw unavailable();
        return lock(current.key());
    }

    public Entry finish(Entry expected, State state, DecisionCode code) { return finish(expected, state, code, expected == null ? null : expected.terminalFacts()); }
    public Entry finish(Entry expected, State state, DecisionCode code, TerminalFacts facts) { return finish(expected, state, code, facts, null); }

    /** Proof only binds an already-applied local command; it cannot authorize or execute a native Message. */
    public Entry finish(Entry expected, State state, DecisionCode code, TerminalFacts facts, AppliedCommand proof) {
        if (state == null || state == State.PENDING || state == State.APPLIED != (proof != null)) throw invalid();
        var retained = retain(expected, facts);
        // Validate the complete positive state shape before any command lookup or write.
        if (expected == null) throw invalid();
        new Entry(expected.key(), expected.caseInstanceId(), expected.registration(), state, retained, expected.createdAt(), expected.nextAttemptAt(),
                expected.failureCount(), expected.createdAt(), code, state == State.APPLIED ? "CASE_COMPLETION_SERVICE" : null);
        var current = current(expected);
        return decide(current, state, code, retained, current.failureCount(), proof);
    }

    private Entry decide(Entry current, State state, DecisionCode code, TerminalFacts facts, int failures, AppliedCommand proof) {
        Instant now = databaseNow();
        new Entry(current.key(), current.caseInstanceId(), current.registration(), state, facts, current.createdAt(), current.nextAttemptAt(), failures,
                now, code, state == State.APPLIED ? "CASE_COMPLETION_SERVICE" : null);
        if (state == State.APPLIED) requireApplied(current, origin(current.key().tenantId(), current.caseInstanceId()), proof);
        if (jdbc.update("UPDATE " + table + " SET state=?,terminal_status=?,terminal_sha256=?,terminal_at=?,failure_count=?,decided_at=?,decision_code=?,applied_command_type=? "
                        + "WHERE tenant_id=? AND completion_command_id=? AND state='PENDING'", state.name(), facts == null ? null : facts.status().name(),
                facts == null ? null : facts.sha256(), facts == null ? null : Timestamp.from(facts.terminalAt()), failures, Timestamp.from(now), code.name(),
                state == State.APPLIED ? "CASE_COMPLETION_SERVICE" : null, current.key().tenantId(), current.key().completionCommandId()) != 1) throw unavailable();
        return lock(current.key());
    }

    private Entry current(Entry expected) {
        requireTransaction(); if (expected == null || expected.state() != State.PENDING) throw unavailable();
        var actual = lock(expected.key()); if (!actual.equals(expected)) throw unavailable(); return actual;
    }
    private static TerminalFacts retain(Entry expected, TerminalFacts facts) {
        if (expected == null) throw invalid();
        if (expected.terminalFacts() != null && facts != null && !expected.terminalFacts().equals(facts)) throw unavailable();
        return facts == null ? expected.terminalFacts() : facts;
    }
    private CaseStartProvenanceStore.Entry origin(long tenant, String caseId) { return provenance.find(tenant, caseId).orElseThrow(CaseCompletionInbox::unavailable); }
    private static void requireOrigin(Entry entry, CaseStartProvenanceStore.Entry origin) {
        var seed = origin.seed();
        if (!"LINKED_BPMN".equals(seed.startKind()) || seed.executor().subjectType() != ProcessActor.SubjectType.SERVICE
                || seed.executor().tenantId() != entry.key().tenantId() || !seed.caseInstanceId().equals(entry.caseInstanceId())
                || !seed.executor().issuerFingerprint().equals(entry.registration().serviceIssuer()) || !seed.executor().subjectId().equals(entry.registration().serviceSubject())
                || !seed.sourceId().equals(entry.registration().sourceId())) throw unavailable();
    }
    private void requireApplied(Entry entry, CaseStartProvenanceStore.Entry origin, AppliedCommand proof) {
        requireOrigin(entry, origin); var registration = entry.registration(); var parameters = new ArrayList<Object>();
        parameters.add(entry.key().tenantId()); parameters.add(registration.serviceIssuer()); parameters.add(registration.serviceSubject()); parameters.add(entry.key().completionCommandId());
        parameters.add(origin.seed().releaseKey()); parameters.add(origin.seed().correlationId());
        String proofPredicate = " AND request_sha256 ~ '^[0-9a-f]{64}$' AND resource_id IS NOT NULL AND resource_id<>''";
        if (proof != null) { proofPredicate = " AND request_sha256=? AND resource_id=?"; parameters.add(proof.requestSha256()); parameters.add(proof.resourceId()); }
        Boolean valid = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + commandsTable
                        + " WHERE tenant_id=? AND subject_type='SERVICE' AND subject_issuer=? AND subject_id=? AND command_type='CASE_COMPLETION_SERVICE' "
                        + "AND command_id=? AND release_key=? AND correlation_id=? AND state='APPLIED'" + proofPredicate + ")", Boolean.class, parameters.toArray());
        if (!Boolean.TRUE.equals(valid)) throw unavailable();
    }

    public Instant databaseNow() { requireTransaction(); var now = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class); if (now == null) throw unavailable(); return now.toInstant(); }
    public void requireStorage(DataSource source, DataSourceTransactionManager manager, String schema) {
        if (this.source != source || this.manager != manager || !this.schema.equals(schema)) throw invalid();
    }
    private void requireTransaction() {
        if (Thread.currentThread().isInterrupted() || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
    }
    private static void discovery(int limit, Duration interval) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Case completion discovery must own its short JDBC transaction");
        if (limit < 1 || limit > 32) throw invalid(); delay(interval);
    }
    private static void delay(Duration delay) {
        if (delay == null || delay.compareTo(Duration.ofMillis(100)) < 0 || delay.compareTo(Duration.ofHours(1)) > 0 || delay.toNanos() % 1_000_000 != 0) throw invalid();
    }
    private static Entry entry(ResultSet rs) throws SQLException {
        try {
            if (!"SERVICE".equals(rs.getString("service_type")) || rs.getObject("failure_count") == null) throw unavailable();
            String status = rs.getString("terminal_status"), hash = rs.getString("terminal_sha256"); var terminalAt = rs.getTimestamp("terminal_at");
            if ((status == null) != (hash == null) || (status == null) != (terminalAt == null)) throw unavailable();
            var facts = status == null ? null : new TerminalFacts(TerminalStatus.valueOf(status), hash, terminalAt.toInstant());
            var decided = rs.getTimestamp("decided_at"); var code = rs.getString("decision_code");
            return new Entry(new DueKey(rs.getLong("tenant_id"), rs.getString("completion_command_id")), rs.getString("case_instance_id"),
                    new RegistrationKey(rs.getLong("tenant_id"), rs.getString("service_issuer"), rs.getString("service_subject"), rs.getString("source_id")),
                    State.valueOf(rs.getString("state")), facts, rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("next_attempt_at").toInstant(), rs.getInt("failure_count"),
                    decided == null ? null : decided.toInstant(), code == null ? null : DecisionCode.valueOf(code), rs.getString("applied_command_type"));
        } catch (RuntimeException malformed) { throw unavailable(); }
    }
    private static String completionId(long tenant, String caseId) { return ProcessRequestDigest.hash(List.of("TIANJI_CASE_COMPLETION_V1", Long.toString(tenant), caseId), Map.of()); }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean text(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i);
            if (c < 32 || c == 127 || Character.isLowSurrogate(c) || Character.isHighSurrogate(c) && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }
    public record RegistrationKey(long tenantId, String serviceIssuer, String serviceSubject, String sourceId) {
        public RegistrationKey { if (tenantId <= 0 || !hash(serviceIssuer) || !text(serviceSubject, 256) || sourceId == null || !sourceId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid(); }
        @Override public String toString() { return "CaseCompletionInbox.RegistrationKey[redacted]"; }
    }
    public record DueKey(long tenantId, String completionCommandId) {
        public DueKey { if (tenantId <= 0 || !hash(completionCommandId)) throw invalid(); }
        @Override public String toString() { return "CaseCompletionInbox.DueKey[redacted]"; }
    }
    public enum State { PENDING, APPLIED, IGNORED_LATE, DEAD }
    public enum DecisionCode { MESSAGE_APPLIED, WAIT_ENDED, MESSAGE_CONSUMED, CASE_TERMINATED, RETRY_EXHAUSTED, SOURCE_UNAVAILABLE, INCONSISTENT_NATIVE_FACT }
    public enum TerminalStatus { COMPLETED, TERMINATED }
    public record TerminalFacts(TerminalStatus status, String sha256, Instant terminalAt) {
        public TerminalFacts { if (status == null || !hash(sha256) || terminalAt == null || terminalAt.getNano() % 1000 != 0) throw invalid(); }
        @Override public String toString() { return "CaseCompletionInbox.TerminalFacts[redacted]"; }
    }
    public record AppliedCommand(String requestSha256, String resourceId) {
        public AppliedCommand { if (!hash(requestSha256) || !text(resourceId, 255)) throw invalid(); }
        @Override public String toString() { return "CaseCompletionInbox.AppliedCommand[redacted]"; }
    }
    public record Entry(DueKey key, String caseInstanceId, RegistrationKey registration, State state,
                        TerminalFacts terminalFacts, Instant createdAt, Instant nextAttemptAt, int failureCount,
                        Instant decidedAt, DecisionCode decisionCode, String appliedCommandType) {
        public Entry {
            if (key == null || !text(caseInstanceId, 255) || registration == null || registration.tenantId() != key.tenantId() || state == null || createdAt == null || nextAttemptAt == null
                    || !completionId(key.tenantId(), caseInstanceId).equals(key.completionCommandId()) || failureCount < 0) throw invalid();
            boolean completed = terminalFacts != null && terminalFacts.status() == TerminalStatus.COMPLETED;
            boolean terminated = terminalFacts != null && terminalFacts.status() == TerminalStatus.TERMINATED;
            boolean valid = switch (state) {
                case PENDING -> decidedAt == null && decisionCode == null && appliedCommandType == null && !terminated;
                case APPLIED -> decidedAt != null && decisionCode == DecisionCode.MESSAGE_APPLIED && "CASE_COMPLETION_SERVICE".equals(appliedCommandType) && completed;
                case IGNORED_LATE -> decidedAt != null && (decisionCode == DecisionCode.WAIT_ENDED || decisionCode == DecisionCode.MESSAGE_CONSUMED) && appliedCommandType == null && !terminated;
                case DEAD -> decidedAt != null && decisionCode != null && Set.of(DecisionCode.CASE_TERMINATED, DecisionCode.RETRY_EXHAUSTED, DecisionCode.SOURCE_UNAVAILABLE, DecisionCode.INCONSISTENT_NATIVE_FACT).contains(decisionCode)
                        && appliedCommandType == null && (decisionCode != DecisionCode.CASE_TERMINATED || terminated);
            };
            if (!valid) throw invalid();
        }
        @Override public String toString() { return "CaseCompletionInbox.Entry[redacted]"; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case completion storage facts are invalid"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Case completion storage or result is unavailable"); }
}
