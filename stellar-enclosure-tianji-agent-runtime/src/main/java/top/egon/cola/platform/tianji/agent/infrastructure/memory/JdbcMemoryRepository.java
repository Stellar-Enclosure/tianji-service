package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One verified owner/scope per instance. SQL values always remain parameters, including IDs and issuer namespaces. */
final class JdbcMemoryRepository {
    private static final String OWNED = "tenant_id=? AND owner_issuer=? AND owner_type=? AND owner_id=? AND scope_id=?";
    private static final String CURRENT = """
            SELECT e.memory_id,e.kind,e.current_revision,e.state,e.content_sha256,e.confirmed,e.sensitivity,
                   e.retention_policy_ref,e.index_model_ref,e.effective_at,e.expires_at,e.created_at,e.updated_at,e.retired_at,
                   r.revision AS stored_revision,r.state AS revision_state,r.content_sha256 AS revision_hash,
                   r.confirmed AS revision_confirmed,r.sensitivity AS revision_sensitivity,
                   r.retention_policy_ref AS revision_retention,r.index_model_ref AS revision_model,
                   r.effective_at AS revision_effective,r.expires_at AS revision_expires,r.written_at,
                   r.supersedes_revision,r.confidence,r.content_json::text AS content_json,r.source_json::text AS source_json,
                   r.source_evidence_sha256,r.source_actor_sha256,r.reason_code,r.redacted_at
            FROM tianji_agent.agent_memory_entry e JOIN tianji_agent.agent_memory_revision r
              ON r.tenant_id=e.tenant_id AND r.owner_issuer=e.owner_issuer AND r.owner_type=e.owner_type
             AND r.owner_id=e.owner_id AND r.scope_id=e.scope_id AND r.memory_id=e.memory_id AND r.revision=e.current_revision
            WHERE e.tenant_id=? AND e.owner_issuer=? AND e.owner_type=? AND e.owner_id=? AND e.scope_id=?
            """;
    private final JdbcTemplate jdbc;
    private final long tenantId;
    private final String issuer;
    private final MemoryOwnerDTO owner;
    private final String scopeId;
    private final MemoryJsonCodec codec;

    JdbcMemoryRepository(JdbcTemplate jdbc, long tenantId, String issuer, MemoryOwnerDTO owner, String scopeId, MemoryJsonCodec codec) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc"); this.tenantId = tenantId; this.issuer = issuer;
        this.owner = Objects.requireNonNull(owner, "owner"); this.scopeId = scopeId; this.codec = Objects.requireNonNull(codec, "codec");
    }

    Instant databaseNow() { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
    long tenantId() { return tenantId; }
    String issuer() { return issuer; }
    MemoryOwnerDTO owner() { return owner; }
    String scopeId() { return scopeId; }

    void lockScope() {
        jdbc.update("""
                INSERT INTO tianji_agent.agent_memory_scope (tenant_id,owner_issuer,owner_type,owner_id,scope_id,created_at,updated_at)
                VALUES (?,?,?,?,?,clock_timestamp(),clock_timestamp()) ON CONFLICT DO NOTHING
                """, key());
        var rows = jdbc.query("SELECT scope_id FROM tianji_agent.agent_memory_scope WHERE " + OWNED + " FOR UPDATE", (rs, row) -> rs.getString(1), key());
        if (rows.size() != 1) throw unavailable();
    }

    void touchScope(Instant now) {
        if (jdbc.update("UPDATE tianji_agent.agent_memory_scope SET updated_at=? WHERE " + OWNED, prefix(new Object[]{timestamp(now)}, key())) != 1) throw unavailable();
    }

    Optional<StoredMemory> current(String id, boolean lock) {
        return single(jdbc.query(CURRENT + " AND e.memory_id=?" + (lock ? " FOR UPDATE OF e" : ""), (rs, row) -> stored(rs), key(id)));
    }

    Optional<StoredMemory> duplicate(MemoryKindEnum kind, String hash) {
        return single(jdbc.query(CURRENT + " AND e.kind=? AND e.content_sha256=? AND e.state IN ('CANDIDATE','ACTIVE') FOR UPDATE OF e",
                (rs, row) -> stored(rs), key(kind.name(), hash)));
    }

    List<StoredMemory> list(Instant now, MemoryKindEnum kind, String after, int limit) {
        var tail = new ArrayList<Object>(); tail.add(timestamp(now)); tail.add(timestamp(now));
        String sql = CURRENT + " AND e.state='ACTIVE' AND e.confirmed AND e.effective_at<=? AND e.expires_at>?";
        if (kind != null) { sql += " AND e.kind=?"; tail.add(kind.name()); }
        if (after != null) { sql += " AND e.memory_id>?"; tail.add(after); }
        sql += " ORDER BY e.memory_id LIMIT ?"; tail.add(limit);
        return jdbc.query(sql, (rs, row) -> stored(rs), key(tail.toArray()));
    }

    long activeCount(Instant now) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_entry WHERE " + OWNED
                + " AND state IN ('CANDIDATE','ACTIVE') AND expires_at>?", Long.class, key(timestamp(now)));
        if (count == null || count < 0) throw unavailable(); return count;
    }

    List<IndexReadiness> indexReadiness(Instant now, MemoryKindEnum kind, MemoryIndexPublication.Binding binding) {
        String sql = """
                SELECT e.memory_id,e.current_revision,e.content_sha256,e.retention_policy_ref,e.sensitivity,e.index_model_ref,
                       m.document_id,m.index_state,m.content_sha256 AS indexed_hash,m.dimensions,m.vector_table,(v.id IS NOT NULL) AS physical_present
                FROM tianji_agent.agent_memory_entry e LEFT JOIN tianji_agent.agent_memory_index_manifest m
                  ON m.tenant_id=e.tenant_id AND m.owner_issuer=e.owner_issuer AND m.owner_type=e.owner_type
                 AND m.owner_id=e.owner_id AND m.scope_id=e.scope_id AND m.memory_id=e.memory_id
                 AND m.content_revision=e.current_revision AND m.model_ref=e.index_model_ref
                LEFT JOIN tianji_agent."%s" v ON v.id=m.document_id
                WHERE e.tenant_id=? AND e.owner_issuer=? AND e.owner_type=? AND e.owner_id=? AND e.scope_id=?
                  AND e.state='ACTIVE' AND e.confirmed AND e.effective_at<=? AND e.expires_at>?
                """.formatted(binding.activeView());
        var tail = new ArrayList<Object>(); tail.add(timestamp(now)); tail.add(timestamp(now));
        if (kind != null) { sql += " AND e.kind=?"; tail.add(kind.name()); }
        return jdbc.query(sql + " LIMIT 10001", (rs, row) -> {
            try {
                return new IndexReadiness(rs.getString("memory_id"), rs.getLong("current_revision"), rs.getString("content_sha256"), rs.getString("retention_policy_ref"),
                        MemorySensitivityEnum.valueOf(rs.getString("sensitivity")), rs.getString("index_model_ref"), rs.getObject("document_id", UUID.class),
                        rs.getString("index_state"), rs.getString("indexed_hash"), rs.getObject("dimensions", Integer.class), rs.getString("vector_table"), rs.getBoolean("physical_present"));
            } catch (RuntimeException malformed) { throw unavailable(); }
        }, key(tail.toArray()));
    }

    void verifySource(TrustedAgentActor actor, MemorySourceDTO source) {
        actorScope(actor); if (source.sourceRunId() == null) return;
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM tianji_agent.agent_run r JOIN tianji_agent.agent_event e
                  ON e.tenant_id=r.tenant_id AND e.run_id=r.run_id
                 WHERE r.tenant_id=? AND r.subject_issuer=? AND r.subject_type=? AND r.subject_id=?
                   AND r.run_id=? AND r.status='COMPLETED' AND e.event_id=?)
                """, Boolean.class, tenantId, issuer, actor.subjectType().name(), actor.subjectId(), source.sourceRunId(), source.sourceEventId());
        if (!Boolean.TRUE.equals(found)) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Memory source run/event was not found in the current owner scope");
    }

    void insert(StoredMemory stored) {
        var entry = stored.entry(); entryScope(entry);
        int inserted = jdbc.update("""
                INSERT INTO tianji_agent.agent_memory_entry
                    (tenant_id,owner_issuer,owner_type,owner_id,scope_id,memory_id,kind,current_revision,state,content_sha256,
                     confirmed,sensitivity,retention_policy_ref,index_model_ref,effective_at,expires_at,created_at,updated_at,retired_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, key(entry.memoryId(), entry.kind().name(), entry.revision(), entry.state().name(), entry.contentSha256(), entry.confirmed(),
                entry.sensitivity().name(), entry.retentionPolicyRef(), stored.indexModelRef(), timestamp(entry.effectiveAt()), timestamp(entry.expiresAt()),
                timestamp(entry.createdAt()), timestamp(entry.updatedAt()), timestamp(stored.retiredAt())));
        if (inserted != 1) throw unavailable();
        insertRevision(stored);
    }

    void replace(StoredMemory stored, long expectedRevision, boolean redactPrior) {
        var entry = stored.entry(); entryScope(entry);
        if (redactPrior) {
            jdbc.update("UPDATE tianji_agent.agent_memory_revision SET state=?,content_json=NULL,source_json=NULL,redacted_at=? WHERE "
                    + OWNED + " AND memory_id=?", prefix(new Object[]{entry.state().name(), timestamp(entry.updatedAt())}, key(entry.memoryId())));
        } else if (jdbc.update("UPDATE tianji_agent.agent_memory_revision SET state='SUPERSEDED' WHERE " + OWNED
                + " AND memory_id=? AND revision=? AND state IN ('CANDIDATE','ACTIVE')", key(entry.memoryId(), expectedRevision)) != 1) throw unavailable();
        insertRevision(stored);
        int changed = jdbc.update("""
                UPDATE tianji_agent.agent_memory_entry SET current_revision=?,state=?,content_sha256=?,confirmed=?,sensitivity=?,
                    retention_policy_ref=?,index_model_ref=?,effective_at=?,expires_at=?,updated_at=?,retired_at=?
                WHERE
                """ + OWNED + " AND memory_id=? AND current_revision=?",
                prefix(new Object[]{entry.revision(), entry.state().name(), entry.contentSha256(), entry.confirmed(), entry.sensitivity().name(),
                        entry.retentionPolicyRef(), stored.indexModelRef(), timestamp(entry.effectiveAt()), timestamp(entry.expiresAt()),
                        timestamp(entry.updatedAt()), timestamp(stored.retiredAt())}, key(entry.memoryId(), expectedRevision)));
        if (changed != 1) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Memory revision changed");
    }

    private void insertRevision(StoredMemory stored) {
        var entry = stored.entry();
        int inserted = jdbc.update("""
                INSERT INTO tianji_agent.agent_memory_revision
                    (tenant_id,owner_issuer,owner_type,owner_id,scope_id,memory_id,revision,supersedes_revision,state,content_json,source_json,
                     content_sha256,source_evidence_sha256,source_actor_sha256,confidence,confirmed,sensitivity,retention_policy_ref,index_model_ref,
                     effective_at,expires_at,written_at,reason_code,redacted_at)
                VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, key(entry.memoryId(), entry.revision(), entry.supersedesRevision(), entry.state().name(),
                entry.content() == null ? null : codec.content(entry.content()), entry.source() == null ? null : codec.source(entry.source()),
                entry.contentSha256(), stored.sourceEvidenceSha256(), stored.sourceActorSha256(), entry.confidence(), entry.confirmed(), entry.sensitivity().name(),
                entry.retentionPolicyRef(), stored.indexModelRef(), timestamp(entry.effectiveAt()), timestamp(entry.expiresAt()), timestamp(entry.updatedAt()),
                stored.reasonCode(), timestamp(stored.retiredAt())));
        if (inserted != 1) throw unavailable();
    }

    Optional<MemoryMutationResultDTO> receipt(TrustedAgentActor actor, String operation, String commandId, String requestHash) {
        actorScope(actor);
        var rows = jdbc.query("SELECT request_sha256,result_memory_id,result_revision,result_outcome,result_state,created_at "
                + "FROM tianji_agent.agent_memory_command_receipt WHERE " + OWNED + " AND actor_type=? AND actor_id=? AND operation=? AND command_id=?",
                (rs, row) -> {
                    if (!requestHash.equals(rs.getString("request_sha256"))) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Memory command content changed");
                    return new MemoryMutationResultDTO(commandId, owner, scopeId, rs.getString("result_memory_id"), rs.getLong("result_revision"),
                            MemoryMutationOutcomeEnum.valueOf(rs.getString("result_outcome")), MemoryStateEnum.valueOf(rs.getString("result_state")), time(rs, "created_at"));
                }, key(actor.subjectType().name(), actor.subjectId(), operation, commandId));
        return single(rows);
    }

    void receipt(TrustedAgentActor actor, String operation, String requestHash, MemoryMutationResultDTO result) {
        actorScope(actor);
        if (!owner.equals(result.owner()) || !scopeId.equals(result.scopeId())) throw unavailable();
        if (jdbc.update("""
                INSERT INTO tianji_agent.agent_memory_command_receipt
                    (tenant_id,owner_issuer,owner_type,owner_id,scope_id,actor_type,actor_id,operation,command_id,request_sha256,
                     result_memory_id,result_revision,result_outcome,result_state,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, key(actor.subjectType().name(), actor.subjectId(), operation, result.commandId(), requestHash, result.memoryId(), result.revision(),
                result.outcome().name(), result.stateAtMutation().name(), timestamp(result.occurredAt()))) != 1) throw unavailable();
    }

    void insertManifest(StoredMemory stored, MemoryIndexPublication.Binding binding, UUID documentId, Instant now) {
        var entry = stored.entry(); entryScope(entry);
        if (entry.state() != MemoryStateEnum.ACTIVE || !stored.indexModelRef().equals(binding.modelRef())) throw unavailable();
        if (jdbc.update("""
                INSERT INTO tianji_agent.agent_memory_index_manifest
                    (tenant_id,owner_issuer,owner_type,owner_id,scope_id,memory_id,content_revision,model_ref,document_id,vector_table,
                     dimensions,content_sha256,index_state,created_at,updated_at,last_verified_at,deleted_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,NULL,NULL)
                """, key(entry.memoryId(), entry.revision(), binding.modelRef(), documentId, binding.vectorTable(), binding.dimensions(),
                entry.contentSha256(), timestamp(now), timestamp(now))) != 1) throw unavailable();
    }

    Optional<RevisionReference> reference(String id, long revision) {
        return single(jdbc.query("SELECT revision,state,content_sha256,index_model_ref,written_at FROM tianji_agent.agent_memory_revision WHERE "
                + OWNED + " AND memory_id=? AND revision=?", (rs, row) -> new RevisionReference(rs.getLong("revision"), MemoryStateEnum.valueOf(rs.getString("state")),
                rs.getString("content_sha256"), rs.getString("index_model_ref"), time(rs, "written_at")), key(id, revision)));
    }

    Optional<Manifest> manifest(String id, long revision, String modelRef) {
        return single(jdbc.query("SELECT * FROM tianji_agent.agent_memory_index_manifest WHERE " + OWNED + " AND memory_id=? AND content_revision=? AND model_ref=?",
                (rs, row) -> manifest(rs), key(id, revision, modelRef)));
    }

    List<Manifest> deletionBatch(String id, Long revision, int limit) {
        String sql = "SELECT * FROM tianji_agent.agent_memory_index_manifest WHERE " + OWNED + " AND memory_id=? AND index_state<>'DELETED'";
        var tail = new ArrayList<Object>(); tail.add(id);
        if (revision != null) { sql += " AND content_revision=?"; tail.add(revision); }
        sql += " ORDER BY content_revision,model_ref LIMIT ?"; tail.add(limit);
        return jdbc.query(sql, (rs, row) -> manifest(rs), key(tail.toArray()));
    }

    boolean activate(Manifest manifest) {
        return jdbc.update("""
                UPDATE tianji_agent.agent_memory_index_manifest m SET index_state='ACTIVE',last_verified_at=clock_timestamp(),updated_at=clock_timestamp()
                FROM tianji_agent.agent_memory_entry e
                WHERE m.tenant_id=? AND m.owner_issuer=? AND m.owner_type=? AND m.owner_id=? AND m.scope_id=?
                  AND m.memory_id=? AND m.content_revision=? AND m.model_ref=? AND m.document_id=? AND m.index_state IN ('PENDING','ACTIVE')
                  AND e.tenant_id=m.tenant_id AND e.owner_issuer=m.owner_issuer AND e.owner_type=m.owner_type AND e.owner_id=m.owner_id
                  AND e.scope_id=m.scope_id AND e.memory_id=m.memory_id AND e.current_revision=m.content_revision
                  AND e.state='ACTIVE' AND e.confirmed AND e.expires_at>clock_timestamp()
                  AND e.content_sha256=m.content_sha256 AND e.index_model_ref=m.model_ref
                """, key(manifest.memoryId(), manifest.revision(), manifest.modelRef(), manifest.documentId())) == 1;
    }

    void deleted(Manifest manifest) {
        if (jdbc.update("""
                UPDATE tianji_agent.agent_memory_index_manifest SET index_state='DELETED',deleted_at=COALESCE(deleted_at,clock_timestamp()),updated_at=clock_timestamp()
                WHERE
                """ + OWNED + " AND memory_id=? AND content_revision=? AND model_ref=? AND document_id=?",
                key(manifest.memoryId(), manifest.revision(), manifest.modelRef(), manifest.documentId())) != 1) throw unavailable();
    }

    private Manifest manifest(ResultSet rs) throws SQLException {
        try {
            var manifest = new Manifest(rs.getString("memory_id"), rs.getLong("content_revision"), rs.getString("model_ref"), rs.getObject("document_id", UUID.class),
                    rs.getString("vector_table"), rs.getInt("dimensions"), rs.getString("content_sha256"), rs.getString("index_state"));
            new MemoryIndexPublication.Binding(manifest.modelRef(), manifest.dimensions(), manifest.vectorTable());
            if (!manifest.documentId().equals(MemoryIndexPublication.vectorDocumentId(tenantId, issuer, owner, scopeId, manifest.memoryId(), manifest.revision(), manifest.modelRef()))) throw unavailable();
            return manifest;
        } catch (AgentLedgerException rejected) { throw rejected; }
        catch (RuntimeException malformed) { throw unavailable(); }
    }

    private StoredMemory stored(ResultSet rs) throws SQLException {
        try {
            long revision = rs.getLong("current_revision"); String state = rs.getString("state"); String hash = rs.getString("content_sha256");
            if (revision != rs.getLong("stored_revision") || !state.equals(rs.getString("revision_state")) || !hash.equals(rs.getString("revision_hash"))
                    || rs.getBoolean("confirmed") != rs.getBoolean("revision_confirmed") || !rs.getString("sensitivity").equals(rs.getString("revision_sensitivity"))
                    || !rs.getString("retention_policy_ref").equals(rs.getString("revision_retention")) || !rs.getString("index_model_ref").equals(rs.getString("revision_model"))
                    || !time(rs, "effective_at").equals(time(rs, "revision_effective")) || !time(rs, "expires_at").equals(time(rs, "revision_expires"))
                    || !time(rs, "updated_at").equals(time(rs, "written_at"))) throw unavailable();
            String content = rs.getString("content_json"); String source = rs.getString("source_json"); String evidence = rs.getString("source_evidence_sha256");
            var entry = new MemoryEntryDTO(owner, scopeId, rs.getString("memory_id"), MemoryKindEnum.valueOf(rs.getString("kind")), revision,
                    rs.getObject("supersedes_revision", Long.class), MemoryStateEnum.valueOf(state), content == null ? null : codec.readContent(content, hash), hash,
                    source == null ? null : codec.readSource(source, evidence), rs.getBigDecimal("confidence"), rs.getBoolean("confirmed"),
                    MemorySensitivityEnum.valueOf(rs.getString("sensitivity")), rs.getString("retention_policy_ref"), time(rs, "effective_at"), time(rs, "expires_at"),
                    time(rs, "created_at"), time(rs, "updated_at"));
            if (!Objects.equals(time(rs, "retired_at"), time(rs, "redacted_at"))) throw unavailable();
            return new StoredMemory(entry, rs.getString("index_model_ref"), evidence, rs.getString("source_actor_sha256"), rs.getString("reason_code"), time(rs, "retired_at"));
        } catch (AgentLedgerException rejected) { throw rejected; }
        catch (RuntimeException malformed) { throw unavailable(); }
    }

    private void actorScope(TrustedAgentActor actor) {
        if (actor == null || actor.tenantId() != tenantId || !issuer.equals(actor.issuerFingerprint())) throw unavailable();
    }
    private void entryScope(MemoryEntryDTO entry) { if (!owner.equals(entry.owner()) || !scopeId.equals(entry.scopeId())) throw unavailable(); }
    private Object[] key(Object... tail) { return prefix(new Object[]{tenantId, issuer, owner.type().name(), owner.id(), scopeId}, tail); }
    private static Object[] prefix(Object[] first, Object[] tail) {
        Object[] result = Arrays.copyOf(first, first.length + tail.length); System.arraycopy(tail, 0, result, first.length, tail.length); return result;
    }
    private static <T> Optional<T> single(List<T> rows) { if (rows.size() > 1) throw unavailable(); return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst()); }
    private static Instant time(ResultSet rs, String column) throws SQLException { var value = rs.getTimestamp(column); return value == null ? null : value.toInstant(); }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    record StoredMemory(MemoryEntryDTO entry, String indexModelRef, String sourceEvidenceSha256, String sourceActorSha256, String reasonCode, Instant retiredAt) {
        StoredMemory {
            Objects.requireNonNull(entry, "entry");
            if (indexModelRef == null || !indexModelRef.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}@[1-9][0-9]{0,18}#[0-9a-f]{64}")
                    || sourceEvidenceSha256 == null || !sourceEvidenceSha256.matches("[0-9a-f]{64}") || sourceActorSha256 == null
                    || !sourceActorSha256.matches("[0-9a-f]{64}") || reasonCode == null || !reasonCode.matches("[A-Z][A-Z0-9_]{0,63}")) throw unavailable();
        }
        @Override public String toString() { return "StoredMemory[redacted]"; }
    }
    record IndexReadiness(String memoryId, long revision, String contentSha256, String retentionPolicyRef, MemorySensitivityEnum sensitivity,
                          String modelRef, UUID documentId, String state, String indexedHash, Integer dimensions, String vectorTable, boolean physicalPresent) { }
    record RevisionReference(long revision, MemoryStateEnum state, String contentSha256, String modelRef, Instant writtenAt) { }
    record Manifest(String memoryId, long revision, String modelRef, UUID documentId, String vectorTable, int dimensions, String contentSha256, String state) { }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory storage is unavailable or changed"); }
}
