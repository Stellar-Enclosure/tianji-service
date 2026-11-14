package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Validated
public class AutomationActivationStore {
    private static final String SCOPED = "tenant_id=? AND definition_key=?";
    private static final String COLUMNS = """
            tenant_id, definition_key, revision, release_key, previous_revision, actor_type, actor_issuer, actor_id,
            command_id, request_sha256, source_sha256, compiled_sha256, dependency_sha256, authorization_sha256,
            dependency_evidence_sha256, release_signature_sha256, trust_generation, admission_proof,
            observed_at, proof_valid_until, activated_at
            """;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final Set<String> ARCHIVE_FIELDS = Set.of("querySha256", "authorizationDecisionId", "authorizationSha256",
            "observedAt", "validUntil", "trustGeneration", "releaseSignatureEnvelopeSha256", "signedPayloadSha256",
            "dependencyEvidenceSha256", "verificationAudit", "proofSha256");

    private final JdbcTemplate jdbc;
    private final String table;
    private final String releases;

    public AutomationActivationStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        if (!AutomationAdmissionQuery.matches(schema, "[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Invalid Process schema");
        }
        table = schema + ".tianji_process_automation_activation";
        releases = AutomationReleaseStore.releaseTable(schema);
    }

    public Instant now() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT pg_catalog.clock_timestamp()::timestamptz(6)",
                OffsetDateTime.class)).toInstant();
    }

    void lockTenant(long tenantId) {
        requireOwningWriteTransaction();
        PlatformReleaseTenantFence.acquireExclusive(jdbc, tenantId);
    }

    private void requireOwningWriteTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) {
            throw new IllegalStateException("Automation activation requires its owning writable transaction");
        }
    }

    public boolean hasPublishedDefinition(long tenantId, @NotNull String definitionKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + releases
                + " WHERE tenant_id=? AND root_definition_key=?)", Boolean.class, tenantId, definitionKey));
    }

    public Optional<AutomationActivationBO> current(long tenantId, @NotNull String definitionKey) {
        return jdbc.query("SELECT " + COLUMNS + " FROM " + table + " WHERE " + SCOPED + " ORDER BY revision DESC LIMIT 1",
                this::row, tenantId, definitionKey).stream().findFirst();
    }

    public Optional<AutomationActivationBO> findByCommand(@NotNull ProcessActor actor, @NotNull String definitionKey,
                                                           @NotNull String commandId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM " + table + " WHERE " + SCOPED
                        + " AND actor_type=? AND actor_issuer=?::char(64) COLLATE \"C\" AND actor_id=? AND command_id=?",
                this::row, actor.tenantId(), definitionKey, actor.subjectType().name(), actor.issuerFingerprint(),
                actor.subjectId(), commandId).stream().findFirst();
    }

    List<AutomationActivationBO> list(long tenantId, String definitionKey, long highWater, long before, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM " + table + " WHERE " + SCOPED
                        + " AND revision<=? AND revision<? ORDER BY revision DESC LIMIT ?",
                this::row, tenantId, definitionKey, highWater, before, limit);
    }

    void insert(@NotNull @Valid AutomationActivationBO row) {
        requireOwningWriteTransaction();
        var proof = row.getProof();
        int affected = jdbc.update("INSERT INTO " + table + " (" + COLUMNS + ") VALUES ("
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)",
                row.getTenantId(), row.getDefinitionKey(), row.getRevision(), row.getReleaseVersion(), row.getPreviousRevision(),
                row.getActor().subjectType().name(), row.getActor().issuerFingerprint(), row.getActor().subjectId(),
                row.getCommandId(), row.getRequestSha256(), row.getSourceManifestSha256(), row.getCompiledManifestSha256(),
                row.getDependencyManifestSha256(), proof.authorizationSha256(), proof.dependencyEvidenceSha256(),
                proof.releaseSignatureEnvelopeSha256(), proof.trustGeneration(), encodeProof(proof),
                utc(proof.observedAt()), utc(proof.validUntil()), utc(row.getActivatedAt()));
        if (affected != 1) throw invalid();
    }

    private AutomationActivationBO row(ResultSet rs, int index) throws SQLException {
        long tenantId = rs.getLong("tenant_id");
        var proof = decodeProof(rs.getString("admission_proof"));
        var activatedAt = instant(rs, "activated_at");
        if (!proof.observedAt().equals(instant(rs, "observed_at")) || !proof.validUntil().equals(instant(rs, "proof_valid_until"))
                || !proof.authorizationSha256().equals(rs.getString("authorization_sha256"))
                || !proof.dependencyEvidenceSha256().equals(rs.getString("dependency_evidence_sha256"))
                || !proof.releaseSignatureEnvelopeSha256().equals(rs.getString("release_signature_sha256"))
                || proof.trustGeneration() != rs.getLong("trust_generation")) throw invalid();
        var activation = new AutomationActivationBO().setTenantId(tenantId).setDefinitionKey(rs.getString("definition_key"))
                .setRevision(rs.getLong("revision")).setPreviousRevision(rs.getObject("previous_revision", Long.class))
                .setReleaseVersion(rs.getString("release_key"))
                .setActor(new ProcessActor(tenantId, ProcessActor.SubjectType.valueOf(rs.getString("actor_type")),
                        rs.getString("actor_id"), rs.getString("actor_issuer"))).setCommandId(rs.getString("command_id"))
                .setRequestSha256(rs.getString("request_sha256")).setSourceManifestSha256(rs.getString("source_sha256"))
                .setCompiledManifestSha256(rs.getString("compiled_sha256")).setDependencyManifestSha256(rs.getString("dependency_sha256"))
                .setProof(proof).setActivatedAt(activatedAt);
        try {
            String requestSha = AutomationAdmissionQuery.activationRequestSha256(activation.getActor(), activation.getDefinitionKey(),
                    activation.getReleaseVersion(), activation.getExpectedActivationRevision(), activation.getCommandId());
            if (!requestSha.equals(activation.getRequestSha256())) throw invalid();
            var query = AutomationAdmissionQuery.activation(AutomationAdmissionQuery.Purpose.ACTIVATE, activation.getActor(),
                    activation.getDefinitionKey(), activation.getReleaseVersion(), activation.getCommandId(), requestSha,
                    activation.getExpectedActivationRevision(), activation.getSourceManifestSha256(),
                    activation.getCompiledManifestSha256(), activation.getDependencyManifestSha256());
            proof.requireCurrent(query.sha256(), activatedAt);
            return activation;
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        }
    }

    private static String encodeProof(AutomationAdmissionProofVO proof) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("querySha256", proof.querySha256());
        wire.put("authorizationDecisionId", proof.authorizationDecisionId());
        wire.put("authorizationSha256", proof.authorizationSha256());
        wire.put("observedAt", proof.observedAt().toString());
        wire.put("validUntil", proof.validUntil().toString());
        wire.put("trustGeneration", proof.trustGeneration());
        wire.put("releaseSignatureEnvelopeSha256", proof.releaseSignatureEnvelopeSha256());
        wire.put("signedPayloadSha256", proof.signedPayloadSha256());
        wire.put("dependencyEvidenceSha256", proof.dependencyEvidenceSha256());
        wire.put("verificationAudit", proof.verificationAudit());
        wire.put("proofSha256", proofSha256(wire));
        return new String(ProcessTypedJson.canonicalBytes(wire, AutomationAdmissionProofVO.MAX_ARCHIVE_BYTES), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static AutomationAdmissionProofVO decodeProof(String raw) {
        try {
            if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > AutomationAdmissionProofVO.MAX_ARCHIVE_BYTES) throw invalid();
            Map<String, Object> wire = JSON.readValue(raw, OBJECT);
            if (!wire.keySet().equals(ARCHIVE_FIELDS) || !Objects.equals(wire.remove("proofSha256"), proofSha256(wire))) throw invalid();
            return new AutomationAdmissionProofVO((String) wire.get("querySha256"), (String) wire.get("authorizationDecisionId"),
                    (String) wire.get("authorizationSha256"), Instant.parse((String) wire.get("observedAt")),
                    Instant.parse((String) wire.get("validUntil")), ((Number) wire.get("trustGeneration")).longValue(),
                    (String) wire.get("releaseSignatureEnvelopeSha256"), (String) wire.get("signedPayloadSha256"),
                    (String) wire.get("dependencyEvidenceSha256"), (Map<String, Object>) wire.get("verificationAudit"));
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static String proofSha256(Map<String, Object> wire) {
        return ProcessTypedJson.sha256("AUTOMATION_ADMISSION_ARCHIVE_V2",
                ProcessTypedJson.canonicalBytes(wire, AutomationAdmissionProofVO.MAX_ARCHIVE_BYTES));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Stored automation activation evidence is inconsistent or unavailable");
    }
}
