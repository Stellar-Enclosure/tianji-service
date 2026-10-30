package top.egon.cola.platform.tianji.process.server.bridge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.execution.ProcessValues;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Durable intent and close evidence. Network calls must occur after these short transactions commit. */
public final class AgentDispatchStore {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private static final TypeReference<Map<String, Object>> INPUT = new TypeReference<>() { };
    private final DataSource source;
    private final DataSourceTransactionManager manager;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String table;

    public AgentDispatchStore(DataSource source, DataSourceTransactionManager manager, String schema) {
        if (source == null || manager == null || manager.getDataSource() != source
                || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        new ProcessApiSchemaVerifier(source, schema).verifyAgentDispatch();
        this.source = source;
        this.manager = manager;
        this.jdbc = new JdbcTemplate(source);
        this.transaction = new TransactionTemplate(manager);
        this.table = '"' + schema + "\".tianji_process_agent_dispatch";
    }

    public void requireStorage(DataSource candidate, DataSourceTransactionManager transactionManager) {
        if (source != candidate || manager != transactionManager) throw invalid();
    }

    /** Called only after the adapter locks and verifies the current native job and Worker lease. */
    public Intent prepare(Seed seed) {
        requireTransaction();
        Objects.requireNonNull(seed, "seed");
        var binding = seed.binding();
        var actor = binding.actor();
        jdbc.update("WITH observed AS (SELECT clock_timestamp() AS now) INSERT INTO " + table + " ("
                        + "tenant_id,source_id,scope_type,scope_id,occurrence_id,subject_issuer,subject_id,idempotency_key,"
                        + "release_key,process_definition_id,process_model_sha256,element_id,topic,schema_version,worker_input_sha256,seed_sha256,"
                        + "agent_definition_key,agent_definition_version,agent_definition_sha256,instruction,input_data,business_key,"
                        + "correlation_id,submission_timeout_ms,created_at,dispatch_not_after,next_check_at,state) "
                        + "SELECT ?,?,'BPMN',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,now,"
                        + "now + (? * interval '1 millisecond'),now,'PENDING' FROM observed "
                        + "ON CONFLICT (tenant_id,occurrence_id) DO NOTHING",
                actor.tenantId(), binding.sourceId(), seed.instanceId(), seed.occurrenceId(), actor.issuerFingerprint(), actor.subjectId(),
                seed.idempotencyKey(), seed.releaseKey(), seed.processDefinitionId(), seed.processModelSha256(), seed.elementId(),
                binding.topic(), binding.schemaVersion(), seed.workerInputSha256(), seed.fingerprint(), binding.definitionKey(), binding.definitionVersion(),
                binding.definitionContentSha256(), binding.instruction(), encode(seed.inputs()), seed.businessKey(), seed.correlationId(),
                binding.submissionTimeout().toMillis(), binding.submissionTimeout().toMillis());
        Intent current = lock(seed.key());
        if (!seed.fingerprint().equals(current.seed().fingerprint())) throw conflict();
        return current;
    }

    /** The active observation is advisory; reconciliation repeats it after cancellation races. */
    public Intent refresh(Key key, boolean processActive) {
        return Objects.requireNonNull(transaction.execute(status -> {
            Intent current = lock(key);
            Instant now = now();
            if (current.state() != State.CLOSED && current.state() != State.CLOSING
                    && (!processActive || current.state() != State.ACKNOWLEDGED && !current.dispatchNotAfter().isAfter(now))) {
                jdbc.update("UPDATE " + table + " SET state='CLOSING',next_check_at=clock_timestamp() WHERE " + where(), args(key));
                current = lock(key);
            }
            return current;
        }));
    }

    /** Records a response even if the submission deadline elapsed while the network call was in flight. */
    public Intent record(Key key, LinkedAgentLookupQuery query, LinkedAgentSubmissionDTO submission) {
        Objects.requireNonNull(submission, "submission");
        return Objects.requireNonNull(transaction.execute(status -> {
            Intent current = lock(key);
            if (!current.query().equals(query)) throw conflict();
            AgentRunDTO run = submission.run();
            if (run != null && (!current.seed().binding().definitionKey().equals(run.definitionKey())
                    || current.seed().binding().definitionVersion() != run.definitionVersion() || run.sessionId() != null
                    || current.runId() != null && !current.runId().equals(run.runId()))) throw conflict();
            if (current.runId() != null && run == null) throw conflict();
            if (current.state() == State.CLOSED && (!submission.closed()
                    || !Objects.equals(current.runId(), run == null ? null : run.runId()))) throw conflict();
            State next = submission.closed() ? State.CLOSED : current.state() == State.PENDING ? State.SUBMITTED : current.state();
            if (next != State.CLOSED && next != State.ACKNOWLEDGED && !current.dispatchNotAfter().isAfter(now())) next = State.CLOSING;
            jdbc.update("UPDATE " + table + " SET state=?,run_id=?,closed_at=CASE WHEN ?='CLOSED' THEN COALESCE(closed_at,clock_timestamp()) "
                            + "ELSE NULL END,next_check_at=clock_timestamp() WHERE " + where(), recordArgs(next, run, key));
            return lock(key);
        }));
    }

    /** Called in the transaction that completes the native Worker job and its receipt. */
    public Intent markAcknowledged(Key key, String runId) {
        requireTransaction();
        Intent current = lock(key);
        if (current.state() == State.ACKNOWLEDGED && Objects.equals(current.runId(), runId)) return current;
        if (current.state() != State.SUBMITTED || !Objects.equals(current.runId(), runId)
                || !current.dispatchNotAfter().isAfter(now())) throw conflict();
        jdbc.update("UPDATE " + table + " SET state='ACKNOWLEDGED',next_check_at=clock_timestamp() WHERE " + where(), args(key));
        return lock(key);
    }

    /** Claims bounded observations, not permission to submit. Overlapping observations can only repeat idempotent close. */
    public List<Intent> due(AgentDispatchBinding binding, int limit, Duration observationInterval) {
        if (binding == null || limit < 1 || limit > 32 || observationInterval == null
                || observationInterval.compareTo(Duration.ofSeconds(1)) < 0 || observationInterval.compareTo(Duration.ofHours(1)) > 0
                || observationInterval.toNanos() % 1_000_000 != 0) throw invalid();
        ProcessActor actor = binding.actor();
        return Objects.requireNonNull(transaction.execute(status -> jdbc.query("WITH candidates AS (SELECT tenant_id,occurrence_id FROM "
                        + table + " WHERE tenant_id=? AND subject_issuer=? AND subject_id=? AND source_id=? AND state<>'CLOSED' "
                        + "AND next_check_at<=clock_timestamp() ORDER BY next_check_at,occurrence_id LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "UPDATE " + table + " d SET next_check_at=clock_timestamp() + (? * interval '1 millisecond') "
                        + "FROM candidates c WHERE d.tenant_id=c.tenant_id AND d.occurrence_id=c.occurrence_id RETURNING d.*",
                (rs, row) -> read(rs), actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), binding.sourceId(), limit,
                observationInterval.toMillis())));
    }

    private Intent lock(Key key) {
        List<Intent> rows = jdbc.query("SELECT * FROM " + table + " WHERE " + where() + " FOR UPDATE", (rs, row) -> read(rs), args(key));
        if (rows.size() != 1) throw new ProcessResourceNotFoundException();
        return rows.getFirst();
    }

    private static Intent read(ResultSet rs) throws SQLException {
        var actor = new ProcessActor(rs.getLong("tenant_id"), ProcessActor.SubjectType.SERVICE,
                rs.getString("subject_id"), rs.getString("subject_issuer"));
        var binding = new AgentDispatchBinding(actor, rs.getString("source_id"), rs.getString("topic"), rs.getString("schema_version"), rs.getString("agent_definition_key"),
                rs.getLong("agent_definition_version"), rs.getString("agent_definition_sha256"), rs.getString("instruction"),
                Duration.ofMillis(rs.getLong("submission_timeout_ms")));
        var seed = new Seed(binding, rs.getString("scope_id"), rs.getString("occurrence_id"), rs.getString("release_key"),
                rs.getString("process_definition_id"), rs.getString("process_model_sha256"), rs.getString("element_id"),
                rs.getString("worker_input_sha256"), rs.getString("business_key"), rs.getString("correlation_id"), decode(rs.getString("input_data")));
        if (!"BPMN".equals(rs.getString("scope_type")) || !seed.fingerprint().equals(rs.getString("seed_sha256"))
                || !seed.idempotencyKey().equals(rs.getString("idempotency_key"))) throw conflict();
        var closed = rs.getTimestamp("closed_at");
        return new Intent(seed, rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("dispatch_not_after").toInstant(),
                State.valueOf(rs.getString("state")), rs.getString("run_id"), closed == null ? null : closed.toInstant());
    }

    private Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant()); }
    private static String where() { return "tenant_id=? AND subject_issuer=? AND subject_id=? AND source_id=? AND scope_type='BPMN' AND scope_id=? AND occurrence_id=?"; }
    private static Object[] args(Key key) {
        return new Object[] {key.actor().tenantId(), key.actor().issuerFingerprint(), key.actor().subjectId(), key.sourceId(), key.instanceId(), key.occurrenceId()};
    }
    private static Object[] recordArgs(State state, AgentRunDTO run, Key key) {
        List<Object> values = new ArrayList<>();
        values.add(state.name());
        values.add(run == null ? null : run.runId());
        values.add(state.name());
        values.addAll(List.of(args(key)));
        return values.toArray();
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) {
            throw new IllegalStateException("Agent dispatch requires the owning Process JDBC transaction");
        }
    }
    private static String encode(Map<String, Object> inputs) {
        try { return MAPPER.writeValueAsString(inputs); }
        catch (JsonProcessingException failure) { throw invalid(); }
    }
    private static Map<String, Object> decode(String value) {
        if (value == null || value.length() > 131_072) throw conflict();
        try { return MAPPER.readValue(value, INPUT); }
        catch (JsonProcessingException failure) { throw conflict(); }
    }
    private static void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent dispatch intent is invalid"); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }

    public enum State { PENDING, SUBMITTED, ACKNOWLEDGED, CLOSING, CLOSED }
    public record Key(ProcessActor actor, String sourceId, String instanceId, String occurrenceId) {
        public Key {
            if (actor == null || actor.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())) throw invalid();
            new AgentProcessLink(sourceId, WorkerScopeTypeEnum.BPMN, instanceId, occurrenceId, "0".repeat(64), Instant.EPOCH);
        }
    }

    public record Seed(AgentDispatchBinding binding, String instanceId, String occurrenceId, String releaseKey,
                       String processDefinitionId, String processModelSha256, String elementId, String workerInputSha256,
                       String businessKey, String correlationId, Map<String, Object> inputs) {
        public Seed {
            Objects.requireNonNull(binding, "binding");
            new Key(binding.actor(), binding.sourceId(), instanceId, occurrenceId);
            hash(processModelSha256);
            hash(workerInputSha256);
            if (releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || elementId == null || !elementId.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || processDefinitionId == null || processDefinitionId.isBlank() || processDefinitionId.length() > 255
                    || businessKey == null || inputs == null || inputs.size() > 16) throw invalid();
            inputs = Map.copyOf(inputs);
            Map<String, ValueType> types = new HashMap<>();
            for (var entry : inputs.entrySet()) {
                ProcessModelValidator.validateVariable(entry.getKey());
                if (entry.getValue() instanceof String) types.put(entry.getKey(), ValueType.STRING);
                else if (entry.getValue() instanceof Long) types.put(entry.getKey(), ValueType.INTEGER);
                else if (entry.getValue() instanceof Boolean) types.put(entry.getKey(), ValueType.BOOLEAN);
                else throw invalid();
            }
            inputs = ProcessValues.inputs(types, inputs);
            new StartAgentRunCommand(binding.definitionKey(), binding.definitionVersion(), null, "intent", businessKey, correlationId,
                    new AgentInputDTO(binding.instruction(), List.of(), inputs), null);
            new LinkedAgentLookupQuery(correlationId, new AgentProcessLink(binding.sourceId(), WorkerScopeTypeEnum.BPMN,
                    instanceId, occurrenceId, binding.definitionContentSha256(), Instant.EPOCH));
            if (encode(inputs).length() > 100_000) throw invalid();
        }
        public Key key() { return new Key(binding.actor(), binding.sourceId(), instanceId, occurrenceId); }
        public String idempotencyKey() {
            return "ad-" + ProcessRequestDigest.hash(List.of("process-agent-occurrence-v1", Long.toString(binding.actor().tenantId()),
                    binding.actor().issuerFingerprint(), binding.actor().subjectId(), binding.sourceId(), instanceId, occurrenceId), Map.of());
        }
        public String fingerprint() {
            return ProcessRequestDigest.hash(List.of("process-agent-dispatch-v1", idempotencyKey(), releaseKey, processDefinitionId,
                    processModelSha256, elementId, binding.topic(), binding.schemaVersion(), workerInputSha256, binding.definitionKey(), Long.toString(binding.definitionVersion()),
                    binding.definitionContentSha256(), binding.instruction(), Long.toString(binding.submissionTimeout().toMillis()),
                    businessKey, correlationId), inputs);
        }
        @Override public String toString() { return "AgentDispatchSeed[redacted]"; }
    }

    public record Intent(Seed seed, Instant createdAt, Instant dispatchNotAfter, State state, String runId, Instant closedAt) {
        public Intent {
            Objects.requireNonNull(seed, "seed");
            Objects.requireNonNull(state, "state");
            if (createdAt == null || !createdAt.plus(seed.binding().submissionTimeout()).equals(dispatchNotAfter)
                    || (state == State.CLOSED) != (closedAt != null) || state == State.PENDING && runId != null
                    || (state == State.SUBMITTED || state == State.ACKNOWLEDGED) && runId == null) throw invalid();
            new LinkedAgentLookupQuery(seed.idempotencyKey(), new AgentProcessLink(seed.binding().sourceId(), WorkerScopeTypeEnum.BPMN,
                    seed.instanceId(), seed.occurrenceId(), seed.binding().definitionContentSha256(), dispatchNotAfter));
        }
        public LinkedAgentLookupQuery query() {
            return new LinkedAgentLookupQuery(seed.idempotencyKey(), new AgentProcessLink(seed.binding().sourceId(), WorkerScopeTypeEnum.BPMN,
                    seed.instanceId(), seed.occurrenceId(), seed.binding().definitionContentSha256(), dispatchNotAfter));
        }
        public LinkedAgentStartCommand command() {
            return new LinkedAgentStartCommand(new StartAgentRunCommand(seed.binding().definitionKey(), seed.binding().definitionVersion(),
                    null, seed.idempotencyKey(), seed.businessKey(), seed.correlationId(),
                    new AgentInputDTO(seed.binding().instruction(), List.of(), seed.inputs()), null), query().link());
        }
        @Override public String toString() { return "AgentDispatchIntent[state=" + state + "]"; }
    }
}
