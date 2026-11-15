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
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Durable intent and close evidence. Network calls must occur after these short transactions commit. */
public final class AgentDispatchStore {
    public static final String TYPED_TOPIC = "tianji-v2-agent-dispatch";
    public static final String TYPED_VERSION = "1";
    private static final String TYPED_INSTRUCTION = "RUNTIME_V2_REFERENCE";
    private static final Set<String> TYPED_INPUT_FIELDS = Set.of("nodeExecutionRef", "inputSha256", "configurationSha256");
    private static final String TYPED_INPUT_SQL = "ARRAY['nodeExecutionRef','inputSha256','configurationSha256']";
    private static final String TYPED_SQL = "(topic='" + TYPED_TOPIC + "' AND schema_version='" + TYPED_VERSION
            + "' AND instruction='" + TYPED_INSTRUCTION + "' AND jsonb_exists_all(input_data," + TYPED_INPUT_SQL
            + ") AND input_data-" + TYPED_INPUT_SQL + "='{}'::jsonb)";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_LONG_FOR_INTS);
    private static final TypeReference<Map<String, Object>> INPUT = new TypeReference<>() { };
    private final DataSource source;
    private final DataSourceTransactionManager manager;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String table;
    private final String schema;

    public AgentDispatchStore(DataSource source, DataSourceTransactionManager manager, String schema) {
        if (source == null || manager == null || manager.getDataSource() != source
                || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        new ProcessApiSchemaVerifier(source, schema).verifyAgentDispatch();
        this.source = source;
        this.manager = manager;
        this.jdbc = new JdbcTemplate(source);
        this.transaction = new TransactionTemplate(manager);
        this.table = '"' + schema + "\".tianji_process_agent_dispatch";
        this.schema = schema;
    }

    public void requireStorage(DataSource candidate, DataSourceTransactionManager transactionManager) {
        if (source != candidate || manager != transactionManager) throw invalid();
    }

    public void requireStorage(DataSource candidate, DataSourceTransactionManager transactionManager, String schema) {
        requireStorage(candidate, transactionManager);
        if (!this.schema.equals(schema)) throw invalid();
    }

    /** Ingress and result application lock the dispatch before any Inbox row. */
    public Intent lockForResult(Key key) {
        requireTransaction();
        return lock(key);
    }

    /** Called only after the adapter locks and verifies the current native job and Worker lease. */
    public Intent prepare(Seed seed) {
        requireTransaction();
        Objects.requireNonNull(seed, "seed");
        if (seed.typed()) throw invalid();
        return insert(seed, null);
    }

    private Intent insert(Seed seed, Instant observed) {
        var binding = seed.binding();
        var actor = binding.actor();
        var values = new ArrayList<Object>();
        if (observed != null) values.add(OffsetDateTime.ofInstant(observed, ZoneOffset.UTC));
        values.addAll(Arrays.asList(
                actor.tenantId(), binding.sourceId(), seed.instanceId(), seed.occurrenceId(), actor.issuerFingerprint(), actor.subjectId(),
                seed.idempotencyKey(), seed.releaseKey(), seed.processDefinitionId(), seed.processModelSha256(), seed.elementId(),
                binding.topic(), binding.schemaVersion(), seed.workerInputSha256(), seed.fingerprint(), binding.definitionKey(), binding.definitionVersion(),
                binding.definitionContentSha256(), binding.instruction(), encode(seed.inputs()), seed.businessKey(), seed.correlationId(),
                binding.submissionTimeout().toMillis(), binding.submissionTimeout().toMillis()));
        String clock = observed == null ? "clock_timestamp()" : "CAST(? AS timestamptz)";
        jdbc.update("WITH observed AS (SELECT " + clock + " AS now) INSERT INTO " + table + " ("
                        + "tenant_id,source_id,scope_type,scope_id,occurrence_id,subject_issuer,subject_id,idempotency_key,"
                        + "release_key,process_definition_id,process_model_sha256,element_id,topic,schema_version,worker_input_sha256,seed_sha256,"
                        + "agent_definition_key,agent_definition_version,agent_definition_sha256,instruction,input_data,business_key,"
                        + "correlation_id,submission_timeout_ms,created_at,dispatch_not_after,next_check_at,state) "
                        + "SELECT ?,?,'BPMN',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,now,"
                        + "now + (? * interval '1 millisecond'),now,'PENDING' FROM observed "
                        + "ON CONFLICT DO NOTHING", values.toArray());
        // A concurrent insert can collide with either the primary key or the occurrence constraint.
        Intent current = lock(seed.key());
        if (!seed.fingerprint().equals(current.seed().fingerprint())) throw conflict();
        return current;
    }

    public Intent prepareTyped(Seed seed, ProcessNodeExecutionBO node) {
        requireTransaction();
        Objects.requireNonNull(seed, "seed");
        Objects.requireNonNull(node, "node");
        AutomationInstanceGuardCommandInterceptor.requireHeld(seed.key().actor().tenantId(), seed.instanceId());
        if (!seed.typed() || node.getKind() != KindEnum.AGENT || node.getTenantId() != seed.key().actor().tenantId()
                || !seed.instanceId().equals(node.getInstanceId()) || !seed.occurrenceId().equals(node.getNativeJobId())
                || !seed.workerInputSha256().equals(node.getInputSha256())
                || !seed.inputs().equals(Map.of("nodeExecutionRef", node.getNodeExecutionRef(), "inputSha256", node.getInputSha256(),
                        "configurationSha256", node.getConfigurationSha256()))) throw conflict();

        // A re-acquired native job uses its original timeout and timestamp, even when the remaining window is shorter.
        var existing = findLocked(seed.key());
        if (existing != null) {
            var expected = withTimeout(seed, existing.seed().binding().submissionTimeout());
            if (!expected.fingerprint().equals(existing.seed().fingerprint())
                    || existing.dispatchNotAfter().isAfter(node.getAttemptDeadlineAt())) throw conflict();
            return existing;
        }
        int open = jdbc.queryForObject("SELECT count(*) FROM " + table
                        + " WHERE tenant_id=? AND scope_id=? AND state<>'CLOSED'", Integer.class, node.getTenantId(), node.getInstanceId());
        if (open >= 256) throw unavailable();
        Instant observed = now();
        long allowed = Math.min(seed.binding().submissionTimeout().toMillis(),
                Duration.between(observed, node.getAttemptDeadlineAt()).toMillis());
        if (allowed < 1000) throw conflict();
        return insert(withTimeout(seed, Duration.ofMillis(allowed)), observed);
    }

    public Intent recordTyped(Key key, LinkedAgentRuntimeDTO outcome) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var current = lock(key);
            requireRuntimeOutcome(current, outcome);
            return recordState(current, outcome.getRunId(), false);
        }));
    }

    static void requireRuntimeOutcome(Intent current, LinkedAgentRuntimeDTO outcome) {
        var seed = current.seed();
        if (!seed.typed() || outcome == null || outcome.getRunId() == null || !outcome.getRunId().matches("[^\\p{Cc}]{1,64}")
                || !seed.idempotencyKey().equals(outcome.getIdempotencyKey())
                || !Objects.equals(seed.inputs().get("nodeExecutionRef"), outcome.getNodeExecutionRef())
                || outcome.getRevision() < 1 || outcome.getRevision() > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || !runtimeStatus(outcome.getState())) throw conflict();
    }

    private static boolean runtimeStatus(String value) {
        return value != null && switch (value) {
            case "CREATED", "QUEUED", "RUNNING", "WAITING", "SUSPENDED", "COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT" -> true;
            default -> false;
        };
    }

    private static Seed withTimeout(Seed seed, Duration timeout) {
        var binding = seed.binding();
        var bounded = new AgentDispatchBinding(binding.actor(), binding.sourceId(), binding.topic(), binding.schemaVersion(),
                binding.definitionKey(), binding.definitionVersion(), binding.definitionContentSha256(), binding.instruction(), timeout);
        return new Seed(bounded, seed.instanceId(), seed.occurrenceId(), seed.releaseKey(), seed.processDefinitionId(),
                seed.processModelSha256(), seed.elementId(), seed.workerInputSha256(), seed.businessKey(), seed.correlationId(), seed.inputs());
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
                    || current.seed().typed() && (run.revision() < 1 || run.revision() > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                            || !runtimeStatus(run.status().name())))) throw conflict();
            return recordState(current, run == null ? null : run.runId(), submission.closed());
        }));
    }

    private Intent recordState(Intent current, String runId, boolean closed) {
        if (current.runId() != null && !current.runId().equals(runId)) throw conflict();
        if (current.state() == State.CLOSED) {
            if (!closed || !Objects.equals(current.runId(), runId)) throw conflict();
            return current;
        }
        State next = closed ? State.CLOSED : current.state() == State.PENDING ? State.SUBMITTED : current.state();
        if (next != State.CLOSED && next != State.ACKNOWLEDGED && !current.dispatchNotAfter().isAfter(now())) next = State.CLOSING;
        Key key = current.seed().key();
        jdbc.update("UPDATE " + table + " SET state=?,run_id=?,closed_at=CASE WHEN ?='CLOSED' THEN COALESCE(closed_at,clock_timestamp()) "
                        + "ELSE NULL END,next_check_at=clock_timestamp() WHERE " + where(), recordArgs(next, runId, key));
        return lock(key);
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
        Objects.requireNonNull(binding, "binding");
        return due(binding.actor(), binding.sourceId(), false, limit, observationInterval);
    }

    public List<Intent> dueTyped(TypedRoute route, int limit, Duration observationInterval) {
        Objects.requireNonNull(route, "route");
        return due(route.actor(), route.sourceId(), true, limit, observationInterval);
    }

    private List<Intent> due(ProcessActor actor, String sourceId, boolean typed, int limit, Duration observationInterval) {
        if (limit < 1 || limit > 32 || observationInterval == null
                || observationInterval.compareTo(Duration.ofSeconds(1)) < 0 || observationInterval.compareTo(Duration.ofHours(1)) > 0
                || observationInterval.toNanos() % 1_000_000 != 0) throw invalid();
        String profile = typed ? TYPED_SQL : "NOT " + TYPED_SQL;
        return Objects.requireNonNull(transaction.execute(status -> jdbc.query("WITH candidates AS (SELECT tenant_id,occurrence_id FROM "
                        + table + " WHERE tenant_id=? AND subject_issuer=? AND subject_id=? AND source_id=? AND state<>'CLOSED' "
                        + "AND " + profile + " "
                        + "AND next_check_at<=clock_timestamp() ORDER BY next_check_at,occurrence_id LIMIT ? FOR UPDATE SKIP LOCKED) "
                        + "UPDATE " + table + " d SET next_check_at=clock_timestamp() + (? * interval '1 millisecond') "
                        + "FROM candidates c WHERE d.tenant_id=c.tenant_id AND d.occurrence_id=c.occurrence_id RETURNING d.*",
                (rs, row) -> read(rs), actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), sourceId, limit,
                observationInterval.toMillis())));
    }

    public int scheduleProcessClosure(ProcessClosureScope scope) {
        requireTransaction();
        if (scope == null) throw invalid();
        List<Intent> candidates = jdbc.query("SELECT * FROM " + table
                        + " WHERE tenant_id=? AND scope_type='BPMN' AND scope_id=? AND state<>'CLOSED' "
                        + "ORDER BY occurrence_id LIMIT 257 FOR UPDATE",
                (rs, row) -> readForClosure(rs), scope.owner().tenantId(), scope.instanceId());
        if (candidates.size() > 256) throw unavailable();

        List<Key> keys = new ArrayList<>(candidates.size());
        var unique = new HashSet<Key>();
        for (Intent candidate : candidates) {
            Seed seed = candidate.seed();
            Key key = seed.key();
            if (candidate.state() == State.CLOSED || key.actor().tenantId() != scope.owner().tenantId()
                    || !seed.instanceId().equals(scope.instanceId()) || !seed.releaseKey().equals(scope.releaseKey())
                    || !seed.processDefinitionId().equals(scope.processDefinitionId())
                    || !seed.processModelSha256().equals(scope.processModelSha256())
                    || !seed.businessKey().equals(scope.businessKey())
                    || !seed.correlationId().equals(scope.correlationId()) || !unique.add(key)) throw unavailable();
            keys.add(key);
        }
        keys = List.copyOf(keys);
        if (keys.isEmpty()) return 0;

        String values = String.join(",", java.util.Collections.nCopies(keys.size(), "(?,?,?,?,'BPMN',?,?)"));
        List<Object> arguments = new ArrayList<>(keys.size() * 6);
        for (Key key : keys) {
            arguments.add(key.actor().tenantId());
            arguments.add(key.actor().issuerFingerprint());
            arguments.add(key.actor().subjectId());
            arguments.add(key.sourceId());
            arguments.add(key.instanceId());
            arguments.add(key.occurrenceId());
        }
        int affected = jdbc.update("UPDATE " + table + " AS d SET state='CLOSING',next_check_at=clock_timestamp() "
                        + "FROM (VALUES " + values + ") AS v(tenant_id,subject_issuer,subject_id,source_id,scope_type,scope_id,occurrence_id) "
                        + "WHERE d.tenant_id=v.tenant_id AND d.subject_issuer=v.subject_issuer AND d.subject_id=v.subject_id "
                        + "AND d.source_id=v.source_id AND d.scope_type=v.scope_type AND d.scope_id=v.scope_id "
                        + "AND d.occurrence_id=v.occurrence_id AND d.state<>'CLOSED'",
                arguments.toArray());
        if (affected != keys.size()) throw unavailable();
        return affected;
    }

    private Intent lock(Key key) {
        var result = findLocked(key);
        if (result == null) throw new ProcessResourceNotFoundException();
        return result;
    }

    private Intent findLocked(Key key) {
        List<Intent> rows = jdbc.query("SELECT * FROM " + table + " WHERE " + where() + " FOR UPDATE", (rs, row) -> read(rs), args(key));
        if (rows.size() > 1) throw conflict();
        return rows.isEmpty() ? null : rows.getFirst();
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

    private static Intent readForClosure(ResultSet rs) throws SQLException {
        try { return read(rs); }
        catch (RuntimeException malformed) { throw unavailable(); }
    }

    private Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant()); }
    static String idempotencyKey(Key key) {
        return "ad-" + ProcessRequestDigest.hash(List.of("process-agent-occurrence-v1", Long.toString(key.actor().tenantId()),
                key.actor().issuerFingerprint(), key.actor().subjectId(), key.sourceId(), key.instanceId(), key.occurrenceId()), Map.of());
    }

    private static String where() { return "tenant_id=? AND subject_issuer=? AND subject_id=? AND source_id=? AND scope_type='BPMN' AND scope_id=? AND occurrence_id=?"; }
    private static Object[] args(Key key) {
        return new Object[] {key.actor().tenantId(), key.actor().issuerFingerprint(), key.actor().subjectId(), key.sourceId(), key.instanceId(), key.occurrenceId()};
    }
    private static Object[] recordArgs(State state, String runId, Key key) {
        List<Object> values = new ArrayList<>();
        values.add(state.name());
        values.add(runId);
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
    private static void text(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.trim())) throw invalid();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index)))) throw invalid();
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent dispatch intent is invalid"); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Agent dispatch closure is unavailable"); }

    public enum State { PENDING, SUBMITTED, ACKNOWLEDGED, CLOSING, CLOSED }

    private static boolean typedBinding(AgentDispatchBinding binding) {
        return TYPED_TOPIC.equals(binding.topic()) && TYPED_VERSION.equals(binding.schemaVersion())
                && TYPED_INSTRUCTION.equals(binding.instruction());
    }

    public record TypedRoute(ProcessActor actor, String sourceId, Duration leaseDuration, Duration submissionTimeout) {
        public TypedRoute {
            new Key(actor, sourceId, "instance", "occurrence");
            if (leaseDuration == null || submissionTimeout == null || leaseDuration.compareTo(Duration.ofSeconds(1)) < 0
                    || submissionTimeout.compareTo(Duration.ofMinutes(15)) > 0 || leaseDuration.compareTo(submissionTimeout) >= 0
                    || leaseDuration.toNanos() % 1_000_000 != 0 || submissionTimeout.toNanos() % 1_000_000 != 0) throw invalid();
        }

        public AgentDispatchBinding binding(String templateKey, long templateVersion, String templateSha256) {
            return binding(templateKey, templateVersion, templateSha256, submissionTimeout);
        }

        public AgentDispatchBinding binding(String templateKey, long templateVersion, String templateSha256,
                                           Duration policyDispatchTimeout) {
            if (policyDispatchTimeout == null || !policyDispatchTimeout.isPositive()) throw invalid();
            Duration timeout = policyDispatchTimeout.compareTo(submissionTimeout) < 0
                    ? Duration.ofMillis(policyDispatchTimeout.toMillis()) : submissionTimeout;
            return new AgentDispatchBinding(actor, sourceId, TYPED_TOPIC, TYPED_VERSION, templateKey, templateVersion,
                    templateSha256, TYPED_INSTRUCTION, timeout);
        }
        @Override public String toString() { return "TypedAgentDispatchRoute[redacted]"; }
    }

    public record ProcessClosureScope(ProcessActor owner, String instanceId, String releaseKey,
                                      String processDefinitionId, String processModelSha256,
                                      String businessKey, String correlationId) {
        public ProcessClosureScope {
            if (owner == null || "LEGACY".equals(owner.issuerFingerprint())
                    || releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || correlationId == null || !correlationId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
            text(instanceId, 255);
            text(processDefinitionId, 255);
            hash(processModelSha256);
            text(businessKey, 255);
        }
    }
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
            if (typedBinding(binding) && inputs.keySet().equals(TYPED_INPUT_FIELDS)) {
                if (!(inputs.get("nodeExecutionRef") instanceof String nodeRef)
                        || !nodeRef.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}") || !workerInputSha256.equals(inputs.get("inputSha256"))
                        || !(inputs.get("configurationSha256") instanceof String configurationSha)) throw invalid();
                hash(configurationSha);
            }
            new StartAgentRunCommand(binding.definitionKey(), binding.definitionVersion(), null, "intent", businessKey, correlationId,
                    new AgentInputDTO(binding.instruction(), List.of(), inputs), null);
            new LinkedAgentLookupQuery(correlationId, new AgentProcessLink(binding.sourceId(), WorkerScopeTypeEnum.BPMN,
                    instanceId, occurrenceId, binding.definitionContentSha256(), Instant.EPOCH));
            if (encode(inputs).length() > 100_000) throw invalid();
        }
        public Key key() { return new Key(binding.actor(), binding.sourceId(), instanceId, occurrenceId); }
        public boolean typed() { return typedBinding(binding) && inputs.keySet().equals(TYPED_INPUT_FIELDS); }
        public String idempotencyKey() {
            return AgentDispatchStore.idempotencyKey(key());
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
            if (seed.typed()) throw invalid();
            return new LinkedAgentStartCommand(new StartAgentRunCommand(seed.binding().definitionKey(), seed.binding().definitionVersion(),
                    null, seed.idempotencyKey(), seed.businessKey(), seed.correlationId(),
                    new AgentInputDTO(seed.binding().instruction(), List.of(), seed.inputs()), null), query().link());
        }
        @Override public String toString() { return "AgentDispatchIntent[state=" + state + "]"; }
    }
}
