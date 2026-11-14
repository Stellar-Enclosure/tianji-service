package top.egon.cola.platform.tianji.process.server.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionProofVO;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Validated
public class ProcessInstanceBindingStore {
    static final int MAX_CONFIGURATION_BYTES = 256 * 1024;
    static final int MAX_EVIDENCE_BYTES = 64 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final Set<String> PROOF = Set.of("querySha256", "authorizationDecisionId", "authorizationSha256",
            "observedAt", "validUntil", "trustGeneration", "releaseSignatureEnvelopeSha256", "signedPayloadSha256",
            "dependencyEvidenceSha256", "verificationAudit");
    private static final String COLUMNS = """
            tenant_id, instance_id, release_key, definition_key, owner_type, owner_issuer, owner_id, profile,
            configuration, configuration_sha256, start_request_sha256, created_at, activation_revision, source_command_type,
            source_command_id, resolved_start_sha256, admission_kind, source_event_receipt_id, admission_evidence, admitted_at
            """;

    private final JdbcTemplate jdbc;
    private final String bindings;
    private final String definitions;
    private final String releases;
    private final String activations;

    public ProcessInstanceBindingStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Invalid Process schema");
        bindings = schema + ".tianji_process_instance_binding";
        definitions = schema + ".tianji_process_release_definition";
        releases = schema + ".tianji_process_release";
        activations = schema + ".tianji_process_automation_activation";
    }

    public void insert(@NotNull @Valid AutomationInstanceBindingBO binding) {
        requireTransaction(true);
        requireBinding(binding);
        int count = jdbc.update("INSERT INTO " + bindings + " (" + COLUMNS
                        + ") VALUES (?,?,?,?,?,?,?,'AUTOMATION_V2',?::jsonb,?,?,?,?,'START',?,?,?, ?,?::jsonb,?)",
                binding.getTenantId(), binding.getInstanceId(), binding.getReleaseKey(), binding.getDefinitionKey(),
                binding.getOwner().subjectType().name(), binding.getOwner().issuerFingerprint(), binding.getOwner().subjectId(),
                text(binding.getEffectiveManifest(), MAX_CONFIGURATION_BYTES), binding.getConfigurationSha256(),
                binding.getStartRequestSha256(), utc(binding.getSubmittedAt()), binding.getActivationRevision(),
                binding.getSourceCommandId(), binding.getResolvedStartSha256(), binding.getAdmissionKind().name(),
                binding.getSourceEventReceiptId(), text(encodeProof(binding.getProof()), MAX_EVIDENCE_BYTES), utc(binding.getAdmittedAt()));
        if (count != 1) throw unavailable();
    }

    public Optional<AutomationInstanceBindingBO> find(long tenantId, @NotNull String instanceId) {
        requireTransaction(false);
        return jdbc.query("SELECT " + COLUMNS + " FROM " + bindings + " WHERE tenant_id=? AND instance_id=?",
                this::row, tenantId, instanceId).stream().findFirst();
    }

    public AutomationInstanceBindingBO lockRoot(long tenantId, @NotNull String instanceId) {
        requireTransaction(true);
        return jdbc.query("SELECT " + COLUMNS + " FROM " + bindings + " WHERE tenant_id=? AND instance_id=? FOR UPDATE",
                this::row, tenantId, instanceId).stream().findFirst().orElseThrow(ProcessResourceNotFoundException::new);
    }

    Optional<String> findRawDigest(ProcessActor actor, String commandId) {
        requireTransaction(false);
        return jdbc.query("SELECT start_request_sha256 FROM " + bindings + " WHERE tenant_id=? AND owner_type=?"
                        + " AND owner_issuer=? AND owner_id=? AND source_command_type='START' AND source_command_id=?",
                (rows, index) -> rows.getString(1), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                commandId).stream().findFirst();
    }

    Optional<AutomationInstanceBindingBO> findByCommand(ProcessActor actor, String commandId) {
        requireTransaction(false);
        return jdbc.query("SELECT " + COLUMNS + " FROM " + bindings + " WHERE tenant_id=? AND owner_type=?"
                        + " AND owner_issuer=? AND owner_id=? AND source_command_type='START' AND source_command_id=?",
                this::row, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                commandId).stream().findFirst();
    }

    Optional<ProcessActor> findOwner(long tenantId, String instanceId) {
        requireTransaction(false);
        return jdbc.query("SELECT owner_type,owner_issuer,owner_id FROM " + bindings + " WHERE tenant_id=? AND instance_id=?",
                (rs, index) -> owner(tenantId, rs), tenantId, instanceId).stream().findFirst();
    }

    Optional<NativeDefinitionVO> nativeDefinition(long tenantId, String releaseVersion, String definitionKey) {
        requireTransaction(false);
        return jdbc.query("""
                SELECT definition.engine_definition_id,definition.engine_deployment_id
                FROM %s definition JOIN %s release ON release.tenant_id=definition.tenant_id AND release.release_key=definition.release_key
                WHERE definition.tenant_id=? AND definition.release_key=? AND definition.definition_key=?
                  AND definition.model_type='BPMN' AND release.state='ACTIVE'
                """.formatted(definitions, releases), (rs, index) -> new NativeDefinitionVO(rs.getString(1), rs.getString(2)),
                tenantId, releaseVersion, definitionKey).stream().findFirst();
    }

    Optional<ActivationEvidenceVO> activationEvidence(AutomationInstanceBindingBO binding) {
        requireTransaction(false);
        return jdbc.query("SELECT source_sha256,compiled_sha256,dependency_sha256 FROM " + activations
                        + " WHERE tenant_id=? AND definition_key=? AND revision=? AND release_key=?",
                (rs, index) -> new ActivationEvidenceVO(rs.getString(1), rs.getString(2), rs.getString(3)),
                binding.getTenantId(), binding.getDefinitionKey(), binding.getActivationRevision(), binding.getReleaseKey()).stream().findFirst();
    }

    void requireConfigurationFits(Map<String, Object> manifest) {
        String encoded = text(manifest, MAX_CONFIGURATION_BYTES);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT octet_length((?::jsonb)::text)<=?", Boolean.class,
                encoded, MAX_CONFIGURATION_BYTES))) throw new IllegalArgumentException("Automation configuration exceeds its storage limit");
    }

    Instant now() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT pg_catalog.clock_timestamp()::timestamptz(6)", OffsetDateTime.class))
                .toInstant();
    }

    private void requireTransaction(boolean writing) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())
                || writing && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw unavailable();
    }

    private AutomationInstanceBindingBO row(ResultSet rs, int index) throws SQLException {
        try {
            if (!"AUTOMATION_V2".equals(rs.getString("profile")) || !"START".equals(rs.getString("source_command_type"))) throw unavailable();
            long tenantId = rs.getLong("tenant_id");
            var configuration = immutableObject(object(rs.getString("configuration"), MAX_CONFIGURATION_BYTES));
            var binding = new AutomationInstanceBindingBO().setTenantId(tenantId).setInstanceId(rs.getString("instance_id"))
                    .setReleaseKey(rs.getString("release_key")).setDefinitionKey(rs.getString("definition_key")).setOwner(owner(tenantId, rs))
                    .setEffectiveManifest(configuration).setConfigurationSha256(rs.getString("configuration_sha256"))
                    .setStartRequestSha256(rs.getString("start_request_sha256")).setSubmittedAt(instant(rs, "created_at"))
                    .setActivationRevision(rs.getLong("activation_revision")).setSourceCommandId(rs.getString("source_command_id"))
                    .setResolvedStartSha256(rs.getString("resolved_start_sha256"))
                    .setAdmissionKind(AutomationInstanceBindingBO.AdmissionKind.valueOf(rs.getString("admission_kind")))
                    .setSourceEventReceiptId(rs.getString("source_event_receipt_id"))
                    .setProof(decodeProof(object(rs.getString("admission_evidence"), MAX_EVIDENCE_BYTES)))
                    .setAdmittedAt(instant(rs, "admitted_at"));
            requireBinding(binding);
            return binding;
        } catch (RuntimeException malformed) {
            throw unavailable();
        }
    }

    private static ProcessActor owner(long tenantId, ResultSet row) throws SQLException {
        return new ProcessActor(tenantId, ProcessActor.SubjectType.valueOf(row.getString("owner_type")),
                row.getString("owner_id"), row.getString("owner_issuer"));
    }

    private static void requireBinding(AutomationInstanceBindingBO binding) {
        if (binding.getOwner() == null || binding.getOwner().tenantId() != binding.getTenantId()
                || !sha256(binding.getOwner().issuerFingerprint())
                || binding.getActivationRevision() < 1 || binding.getActivationRevision() > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || !sha256(binding.getStartRequestSha256())
                || !sha256(binding.getResolvedStartSha256())
                || !configurationSha256(binding.getEffectiveManifest()).equals(binding.getConfigurationSha256())
                || (binding.getAdmissionKind() == AutomationInstanceBindingBO.AdmissionKind.EVENT) != (binding.getSourceEventReceiptId() != null)
                || binding.getSubmittedAt().isBefore(binding.getAdmittedAt())) throw unavailable();
    }

    static String configurationSha256(Map<String, Object> manifest) {
        return ProcessTypedJson.sha256("AUTOMATION_EFFECTIVE_CONFIGURATION_V2",
                ProcessTypedJson.canonicalBytes(manifest, MAX_CONFIGURATION_BYTES));
    }

    private static Map<String, Object> encodeProof(AutomationAdmissionProofVO proof) {
        return Map.of("querySha256", proof.querySha256(), "authorizationDecisionId", proof.authorizationDecisionId(),
                "authorizationSha256", proof.authorizationSha256(), "observedAt", proof.observedAt().toString(),
                "validUntil", proof.validUntil().toString(), "trustGeneration", proof.trustGeneration(),
                "releaseSignatureEnvelopeSha256", proof.releaseSignatureEnvelopeSha256(), "signedPayloadSha256", proof.signedPayloadSha256(),
                "dependencyEvidenceSha256", proof.dependencyEvidenceSha256(), "verificationAudit", proof.verificationAudit());
    }

    private static AutomationAdmissionProofVO decodeProof(Map<String, Object> wire) {
        if (!wire.keySet().equals(PROOF)) throw unavailable();
        return new AutomationAdmissionProofVO((String) wire.get("querySha256"), (String) wire.get("authorizationDecisionId"),
                (String) wire.get("authorizationSha256"), Instant.parse((String) wire.get("observedAt")),
                Instant.parse((String) wire.get("validUntil")), ((Number) wire.get("trustGeneration")).longValue(),
                (String) wire.get("releaseSignatureEnvelopeSha256"), (String) wire.get("signedPayloadSha256"),
                (String) wire.get("dependencyEvidenceSha256"), closedMap(wire.get("verificationAudit")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> closedMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw unavailable();
        return (Map<String, Object>) map;
    }

    private static Map<String, Object> immutableObject(Map<String, Object> source) {
        var copy = new LinkedHashMap<String, Object>();
        source.forEach((key, child) -> copy.put(key, immutableValue(child)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?>) return immutableObject(closedMap(value));
        if (value instanceof List<?> list) return list.stream().map(ProcessInstanceBindingStore::immutableValue).toList();
        return value;
    }

    private static String text(Map<String, Object> object, int limit) {
        return new String(ProcessTypedJson.canonicalBytes(object, limit), StandardCharsets.UTF_8);
    }

    private static Map<String, Object> object(String raw, int maxBytes) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw unavailable();
        return JSON.readValue(raw, OBJECT);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Automation instance binding evidence is unavailable");
    }

    record NativeDefinitionVO(String definitionId, String deploymentId) { }
    record ActivationEvidenceVO(String sourceSha256, String compiledSha256, String dependencySha256) { }
}
