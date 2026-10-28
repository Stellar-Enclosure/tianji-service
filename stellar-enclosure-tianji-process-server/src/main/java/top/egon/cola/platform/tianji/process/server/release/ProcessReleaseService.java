package top.egon.cola.platform.tianji.process.server.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;

import javax.sql.DataSource;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Internal release transaction; callers must authorize the trusted actor before invoking it. */
public final class ProcessReleaseService {
    private static final int MAX_MODELS = 16;
    private static final int MAX_BUNDLE_BYTES = 4 * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> SCHEMA = new TypeReference<>() { };
    private final SpringProcessEngineConfiguration process;
    private final SpringDmnEngineConfiguration dmn;
    private final ProcessModelValidator validator;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final String schema;
    private final boolean decisionTasksEnabled;

    public ProcessReleaseService(SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration dmn,
                                 ProcessModelValidator validator) {
        this(process, dmn, validator, false);
    }

    public ProcessReleaseService(SpringProcessEngineConfiguration process, SpringDmnEngineConfiguration dmn,
                                 ProcessModelValidator validator, boolean decisionTasksEnabled) {
        this.process = Objects.requireNonNull(process, "process");
        this.dmn = Objects.requireNonNull(dmn, "dmn");
        this.validator = Objects.requireNonNull(validator, "validator");
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || dmn.getTransactionManager() != manager || manager.getDataSource() == null
                || unwrap(process.getDataSource()) != manager.getDataSource() || unwrap(dmn.getDataSource()) != manager.getDataSource()
                || process.getRepositoryService() == null || dmn.getDmnRepositoryService() == null
                || process.getBeans() == null || !process.getBeans().isEmpty() || dmn.getBeans() == null || !dmn.getBeans().isEmpty()
                || process.getDatabaseSchema() == null || !process.getDatabaseSchema().matches("[a-z_][a-z0-9_]{0,62}")
                || !process.getDatabaseSchema().equals(dmn.getDatabaseSchema())
                || decisionTasksEnabled && (!(process.getActivityBehaviorFactory() instanceof PinnedDecisionActivityBehaviorFactory)
                || !dmn.isHistoryEnabled() || dmn.getDmnHistoryService() == null)) throw invalidConfiguration();
        this.decisionTasksEnabled = decisionTasksEnabled;
        this.schema = process.getDatabaseSchema();
        this.jdbc = new JdbcTemplate(manager.getDataSource());
        this.write = new TransactionTemplate(manager);
        this.read = new TransactionTemplate(manager);
        this.read.setReadOnly(true);
        this.read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Release publish(ProcessActor actor, String releaseKey, List<ModelResource> resources) {
        Objects.requireNonNull(actor, "actor");
        requireReleaseKey(releaseKey);
        List<PreparedModel> models = prepare(resources);
        String manifestHash = manifestHash(models);
        return Objects.requireNonNull(write.execute(status -> publishInTransaction(actor, releaseKey, manifestHash, models)));
    }

    public Release get(ProcessActor actor, String releaseKey) {
        Objects.requireNonNull(actor, "actor");
        requireReleaseKey(releaseKey);
        return Objects.requireNonNull(read.execute(status -> load(actor.tenantId(), releaseKey, false)));
    }

    /** Internal lookup for an already-running Flowable instance, not a caller authorization check. */
    public Release getForProcessDefinition(long tenantId, String definitionId) {
        if (tenantId <= 0 || definitionId == null || definitionId.isBlank() || definitionId.length() > 255) throw unavailable();
        return Objects.requireNonNull(read.execute(status -> {
            List<String> keys = jdbc.query("SELECT d.release_key FROM " + table("tianji_process_release_definition")
                            + " d JOIN " + table("tianji_process_release") + " r ON r.tenant_id=d.tenant_id AND r.release_key=d.release_key "
                            + "WHERE d.tenant_id=? AND d.model_type='BPMN' AND d.engine_definition_id=? AND r.state='ACTIVE'",
                    (rs, row) -> rs.getString("release_key"), tenantId, definitionId);
            if (keys.size() != 1) throw unavailable();
            return load(tenantId, keys.getFirst(), false);
        }));
    }

    private Release publishInTransaction(ProcessActor actor, String releaseKey, String manifestHash,
                                         List<PreparedModel> models) {
        int inserted = jdbc.update("INSERT INTO " + table("tianji_process_release") + " "
                        + "(tenant_id, release_key, state, manifest_sha256, publisher_type, publisher_id, publisher_issuer, created_at) "
                        + "VALUES (?, ?, 'PREPARING', ?, ?, ?, ?, clock_timestamp()) ON CONFLICT DO NOTHING",
                actor.tenantId(), releaseKey, manifestHash, actor.subjectType().name(), actor.subjectId(), actor.issuerFingerprint());
        if (inserted == 0) {
            Release existing = load(actor.tenantId(), releaseKey, true);
            if (!existing.manifestSha256().equals(manifestHash)) throw conflict();
            return existing;
        }
        for (PreparedModel model : models) {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { },
                    "tianji:release:" + schema + ":" + actor.tenantId() + ":" + model.model().type() + ":" + model.model().definitionKey());
        }
        for (PreparedModel model : models) {
            Definition definition = deploy(actor.tenantId(), releaseKey, model);
            jdbc.update("INSERT INTO " + table("tianji_process_release_definition") + " "
                            + "(tenant_id, release_key, model_type, definition_key, engine_definition_id, engine_deployment_id, "
                            + "engine_version, model_sha256, input_schema) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))",
                    actor.tenantId(), releaseKey, definition.type().name(), definition.definitionKey(), definition.engineDefinitionId(),
                    definition.engineDeploymentId(), definition.engineVersion(), definition.modelSha256(), encodeSchema(definition.inputs()));
        }
        if (jdbc.update("UPDATE " + table("tianji_process_release") + " SET state='ACTIVE' "
                + "WHERE tenant_id=? AND release_key=? AND state='PREPARING'", actor.tenantId(), releaseKey) != 1) {
            throw new IllegalStateException("Process release could not be activated");
        }
        return load(actor.tenantId(), releaseKey, false);
    }

    private Definition deploy(long tenantId, String releaseKey, PreparedModel prepared) {
        ValidatedProcessModel model = prepared.model();
        String tenant = Long.toString(tenantId);
        String resource = model.definitionKey();
        if (model.type() == Type.BPMN) {
            var deployment = process.getRepositoryService().createDeployment().tenantId(tenant).name("tianji:" + releaseKey)
                    .addBytes(resource + ".bpmn20.xml", model.xml()).deploy();
            List<ProcessDefinition> definitions = process.getRepositoryService().createProcessDefinitionQuery()
                    .deploymentId(deployment.getId()).processDefinitionTenantId(tenant).list();
            if (definitions.size() != 1 || !resource.equals(definitions.getFirst().getKey())) throw invalidDeployment();
            ProcessDefinition definition = definitions.getFirst();
            return new Definition(Type.BPMN, resource, definition.getId(), deployment.getId(), definition.getVersion(),
                    prepared.modelHash(), prepared.inputs());
        }
        var deployment = dmn.getDmnRepositoryService().createDeployment().tenantId(tenant).name("tianji:" + releaseKey)
                .addDmnBytes(resource + ".dmn", model.xml()).deploy();
        List<DmnDecision> definitions = dmn.getDmnRepositoryService().createDecisionQuery()
                .deploymentId(deployment.getId()).decisionTenantId(tenant).list();
        if (definitions.size() != 1 || !resource.equals(definitions.getFirst().getKey())) throw invalidDeployment();
        DmnDecision definition = definitions.getFirst();
        return new Definition(Type.DMN, resource, definition.getId(), deployment.getId(), definition.getVersion(),
                prepared.modelHash(), prepared.inputs());
    }

    private Release load(long tenantId, String releaseKey, boolean lock) {
        List<ReleaseRow> rows = jdbc.query("SELECT manifest_sha256, state, created_at FROM " + table("tianji_process_release")
                        + " WHERE tenant_id=? AND release_key=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new ReleaseRow(rs.getString("manifest_sha256"), rs.getString("state"),
                        rs.getTimestamp("created_at").toInstant()), tenantId, releaseKey);
        if (rows.size() != 1 || !"ACTIVE".equals(rows.getFirst().state())) throw unavailable();
        List<Definition> definitions = jdbc.query("SELECT model_type, definition_key, engine_definition_id, "
                        + "engine_deployment_id, engine_version, model_sha256, input_schema::text AS input_schema FROM "
                        + table("tianji_process_release_definition")
                        + " WHERE tenant_id=? AND release_key=? ORDER BY model_type, definition_key COLLATE \"C\"",
                (rs, row) -> new Definition(Type.valueOf(rs.getString("model_type")), rs.getString("definition_key"),
                        rs.getString("engine_definition_id"), rs.getString("engine_deployment_id"), rs.getInt("engine_version"),
                        rs.getString("model_sha256"), decodeSchema(rs.getString("input_schema"))), tenantId, releaseKey);
        if (definitions.isEmpty() || definitions.size() > MAX_MODELS) throw unavailable();
        ReleaseRow row = rows.getFirst();
        return new Release(releaseKey, row.hash(), row.createdAt(), definitions);
    }

    private List<PreparedModel> prepare(List<ModelResource> resources) {
        if (resources == null || resources.isEmpty() || resources.size() > MAX_MODELS) throw invalid();
        List<PreparedModel> prepared = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int size = 0;
        for (ModelResource resource : resources) {
            if (resource == null || resource.type() == null) throw invalid();
            byte[] xml = resource.xml();
            size += xml.length;
            if (size > MAX_BUNDLE_BYTES || !keys.add(resource.type().name() + ":" + resource.definitionKey())) throw invalid();
            ValidatedProcessModel model = resource.type() == Type.BPMN
                    ? validator.validateBpmn(xml, resource.definitionKey()) : validator.validateDmn(xml, resource.definitionKey());
            Map<String, ValueType> inputs = checkedSchema(resource.inputs());
            if (model.type() == Type.DMN) {
                if (!inputs.isEmpty() && !inputs.equals(model.inputs())) throw invalid();
                inputs = model.inputs();
            }
            prepared.add(new PreparedModel(model, hash(model.xml()), inputs));
        }
        for (PreparedModel processModel : prepared) {
            if (processModel.model().type() != Type.BPMN || processModel.model().decisionTasks().isEmpty()) continue;
            if (!decisionTasksEnabled) throw invalid();
            String decisionKey = processModel.model().decisionTasks().values().iterator().next();
            PreparedModel decision = prepared.stream().filter(item -> item.model().type() == Type.DMN
                    && item.model().definitionKey().equals(decisionKey)).findFirst().orElseThrow(ProcessReleaseService::invalid);
            validator.validateDecisionBinding(processModel.model(), processModel.inputs(), decision.model());
        }
        prepared.sort(Comparator.comparing((PreparedModel item) -> item.model().type().name())
                .thenComparing(item -> item.model().definitionKey()));
        return List.copyOf(prepared);
    }

    private static String manifestHash(List<PreparedModel> models) {
        MessageDigest digest = sha256();
        try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            output.writeInt(models.size());
            for (PreparedModel item : models) {
                writeField(output, item.model().type().name().getBytes(StandardCharsets.UTF_8));
                writeField(output, item.model().definitionKey().getBytes(StandardCharsets.UTF_8));
                writeField(output, item.model().xml());
                Map<String, ValueType> sorted = new TreeMap<>(item.inputs());
                output.writeInt(sorted.size());
                for (var entry : sorted.entrySet()) {
                    writeField(output, entry.getKey().getBytes(StandardCharsets.UTF_8));
                    writeField(output, entry.getValue().name().getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Process release digest could not be calculated");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void writeField(DataOutputStream output, byte[] bytes) throws IOException {
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String hash(byte[] value) { return HexFormat.of().formatHex(sha256().digest(value)); }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }

    private static Map<String, ValueType> checkedSchema(Map<String, ValueType> schema) {
        if (schema == null || schema.size() > 16) throw invalid();
        Map<String, ValueType> checked = new TreeMap<>();
        schema.forEach((key, value) -> {
            ProcessModelValidator.validateVariable(key);
            if (value == null) throw invalid();
            checked.put(key, value);
        });
        return Map.copyOf(checked);
    }

    private static String encodeSchema(Map<String, ValueType> schema) {
        try { return MAPPER.writeValueAsString(new TreeMap<>(schema)); }
        catch (JsonProcessingException failure) { throw invalid(); }
    }

    private static Map<String, ValueType> decodeSchema(String json) {
        try {
            Map<String, String> raw = MAPPER.readValue(json, SCHEMA);
            Map<String, ValueType> result = new TreeMap<>();
            raw.forEach((key, value) -> result.put(key, ValueType.valueOf(value)));
            return checkedSchema(result);
        } catch (JsonProcessingException | RuntimeException failure) {
            throw unavailable();
        }
    }

    private String table(String name) { return '"' + schema + "\"." + name; }
    private static DataSource unwrap(DataSource source) {
        return source instanceof TransactionAwareDataSourceProxy proxy ? proxy.getTargetDataSource() : source;
    }
    private static void requireReleaseKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Process release request is invalid"); }
    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }
    private static IllegalStateException invalidDeployment() { return new IllegalStateException("Process model deployment is inconsistent"); }
    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("Process release requires isolated engines and a shared JDBC transaction manager");
    }

    public record ModelResource(Type type, String definitionKey, byte[] xml, Map<String, ValueType> inputs) {
        public ModelResource {
            if (type == null || definitionKey == null || xml == null || xml.length == 0
                    || xml.length > SafeModelXml.MAX_BYTES || inputs == null) throw invalid();
            xml = xml.clone();
            inputs = checkedSchema(inputs);
        }
        @Override public byte[] xml() { return xml.clone(); }
    }

    public record Definition(Type type, String definitionKey, String engineDefinitionId, String engineDeploymentId,
                             int engineVersion, String modelSha256, Map<String, ValueType> inputs) {
        public Definition { inputs = Map.copyOf(inputs); }
    }

    public record Release(String releaseKey, String manifestSha256, Instant createdAt, List<Definition> definitions) {
        public Release { definitions = List.copyOf(definitions); }
    }

    private record PreparedModel(ValidatedProcessModel model, String modelHash, Map<String, ValueType> inputs) { }
    private record ReleaseRow(String hash, String state, Instant createdAt) { }
}
