package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.cola.component.agentflow.memory.jdbc.JdbcMemoryStore.HostFenceVO;
import top.egon.cola.component.agentflow.api.memory.MemoryWriteResult;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    private static final String HOST_CURRENT = """
            SELECT e.*,e.current_revision AS stored_revision,e.state AS revision_state,e.content_sha256 AS revision_hash,
                   e.confirmed AS revision_confirmed,e.sensitivity AS revision_sensitivity,
                   e.retention_policy_ref AS revision_retention,e.index_model_ref AS revision_model,
                   e.effective_at AS revision_effective,e.expires_at AS revision_expires
            FROM (
                SELECT f.scope_hash,f.fact_id AS memory_id,f.fact_type AS kind,f.created_at,f.current_revision,f.updated_at,
                       r.state,r.content_sha256,r.confirmed,r.effective_at,r.expires_at,r.redacted_at,
                       r.redacted_at AS retired_at,r.created_at AS written_at,r.content::text AS content_json,
                       r.host_metadata->>'indexModelRef' AS index_model_ref,
                       r.host_metadata->>'retentionPolicyRef' AS retention_policy_ref,
                       r.host_metadata->>'sensitivity' AS sensitivity,
                       r.host_metadata->>'sourceEvidenceSha256' AS source_evidence_sha256,
                       r.host_metadata->>'sourceActorSha256' AS source_actor_sha256,
                       r.host_metadata->>'reasonCode' AS reason_code,
                       r.host_metadata->>'confidence' AS confidence,
                       (r.host_metadata->>'supersedesRevision')::bigint AS supersedes_revision,
                       CASE WHEN r.host_metadata->'source'='null'::jsonb THEN NULL
                            ELSE (r.host_metadata->'source')::text END AS source_json,
                       r.content_profile,r.host_metadata::text AS host_metadata
                FROM %s.fact f JOIN %s.revision r
                  ON r.scope_hash=f.scope_hash AND r.fact_id=f.fact_id AND r.revision=f.current_revision
            ) e WHERE e.scope_hash=?
            """;
    private static final Set<String> HOST_METADATA = Set.of("schemaVersion", "source", "sourceEvidenceSha256", "sourceActorSha256",
            "confidence", "sensitivity", "retentionPolicyRef", "indexModelRef", "supersedesRevision", "reasonCode");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    private final long tenantId;
    private final String issuer;
    private final MemoryOwnerDTO owner;
    private final String scopeId;
    private final MemoryJsonCodec codec;
    private String hostSchema;
    private String hostScopeHash;
    private HostFenceVO fence;
    private boolean absent;

    JdbcMemoryRepository(JdbcTemplate jdbc, long tenantId, String issuer, MemoryOwnerDTO owner, String scopeId, MemoryJsonCodec codec) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc"); this.tenantId = tenantId; this.issuer = issuer;
        this.owner = Objects.requireNonNull(owner, "owner"); this.scopeId = scopeId; this.codec = Objects.requireNonNull(codec, "codec");
    }

    Instant databaseNow() { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
    long tenantId() { return tenantId; }
    String issuer() { return issuer; }
    MemoryOwnerDTO owner() { return owner; }
    String scopeId() { return scopeId; }

    JdbcTemplate jdbc() { return jdbc; }

    JdbcMemoryRepository absent() {
        absent = true;
        return this;
    }

    JdbcMemoryRepository fenced(String schema, String scopeHash, HostFenceVO current) {
        hostSchema = schema;
        hostScopeHash = scopeHash;
        fence = current;
        return this;
    }

    long authorityEpoch() { return fence == null ? 0 : fence.authorityEpoch(); }
    boolean component() { return fence != null && "COMPONENT".equals(fence.storageRef()); }
    boolean frozen() { return fence != null && fence.frozen(); }

    boolean scopeExists() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM tianji_agent.agent_memory_scope WHERE " + OWNED + ')',
                Boolean.class, key()));
    }

    long legacyRevisionCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE " + OWNED, Long.class, key());
        if (count == null || count < 0) throw unavailable(); return count;
    }

    private long advanceRevision(Instant now) {
        return jdbc.query("""
                UPDATE %s.scope SET high_water_revision=high_water_revision+1,updated_at=GREATEST(updated_at,?)
                WHERE scope_hash=? AND storage_ref=? AND authority_epoch=? AND state='ACTIVE'
                RETURNING high_water_revision
                """.formatted(hostSchema), rs -> {
                    if (!rs.next()) throw unavailable();
                    return rs.getLong(1);
                }, timestamp(now), hostScopeHash, fence.storageRef(), fence.authorityEpoch());
    }

    private String headSql() { return component() ? HOST_CURRENT.formatted(hostSchema, hostSchema) : CURRENT; }
    private Object[] headKey(Object... tail) { return component() ? prefix(new Object[]{hostScopeHash}, tail) : key(tail); }
    private String headLock() { return component() ? "" : " FOR UPDATE OF e"; }


    void lockScope() {
        if (component() || absent) return;
        jdbc.update("""
                INSERT INTO tianji_agent.agent_memory_scope (tenant_id,owner_issuer,owner_type,owner_id,scope_id,created_at,updated_at)
                VALUES (?,?,?,?,?,clock_timestamp(),clock_timestamp()) ON CONFLICT DO NOTHING
                """, key());
        var rows = jdbc.query("SELECT scope_id FROM tianji_agent.agent_memory_scope WHERE " + OWNED + " FOR UPDATE", (rs, row) -> rs.getString(1), key());
        if (rows.size() != 1) throw unavailable();
    }

    void touchScope(Instant now) {
        if (component() || absent) return;
        if (jdbc.update("UPDATE tianji_agent.agent_memory_scope SET updated_at=? WHERE " + OWNED, prefix(new Object[]{timestamp(now)}, key())) != 1) throw unavailable();
    }

    Optional<StoredMemory> current(String id, boolean lock) {
        if (absent) return Optional.empty();
        return single(jdbc.query(headSql() + " AND e.memory_id=?" + (lock ? headLock() : ""), (rs, row) -> stored(rs), headKey(id)));
    }

    Optional<StoredMemory> duplicate(MemoryKindEnum kind, String hash) {
        if (absent) return Optional.empty();
        return single(jdbc.query(headSql() + " AND e.kind=? AND e.content_sha256=? AND e.state IN ('CANDIDATE','ACTIVE') LIMIT 2" + headLock(),
                (rs, row) -> stored(rs), headKey(kind.name(), hash)));
    }

    List<StoredMemory> list(Instant now, MemoryKindEnum kind, String after, int limit) {
        return list(now, kind, after, limit, null);
    }

    List<StoredMemory> list(Instant now, MemoryKindEnum kind, String after, int limit, List<PgVectorMemoryIndex.Allowed> allowed) {
        return listAuthorized(now, kind == null ? null : Set.of(kind), after, limit, allowed);
    }

    List<StoredMemory> listAuthorized(Instant now, Set<MemoryKindEnum> types, String after, int limit, List<PgVectorMemoryIndex.Allowed> allowed) {
        if (absent || types != null && types.isEmpty() || allowed != null && allowed.isEmpty()) return List.of();
        var tail = new ArrayList<Object>(); tail.add(timestamp(now)); tail.add(timestamp(now));
        String sql = headSql() + " AND e.state='ACTIVE' AND e.confirmed AND e.effective_at<=? AND e.expires_at>?";
        if (types != null) {
            sql += " AND e.kind IN (" + String.join(",", java.util.Collections.nCopies(types.size(), "?")) + ')';
            tail.addAll(types.stream().map(Enum::name).sorted().toList());
        }
        if (after != null) { sql += " AND e.memory_id>?"; tail.add(after); }
        if (allowed != null) {
            var alternatives = new ArrayList<String>();
            for (var item : allowed) { alternatives.add("(e.retention_policy_ref=? AND e.sensitivity=?)"); tail.add(item.retentionPolicyRef()); tail.add(item.sensitivity().name()); }
            sql += " AND (" + String.join(" OR ", alternatives) + ')';
        }
        sql += " ORDER BY e.memory_id LIMIT ?"; tail.add(limit);
        return jdbc.query(sql, (rs, row) -> stored(rs), headKey(tail.toArray()));
    }

    long activeCount(Instant now) {
        if (component()) {
            Long count = jdbc.queryForObject("SELECT count(*) FROM (" + headSql() + " AND e.state IN ('CANDIDATE','ACTIVE') AND e.expires_at>?) live",
                    Long.class, headKey(timestamp(now)));
            if (count == null || count < 0) throw unavailable();
            return count;
        }
        Long count = jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_entry WHERE " + OWNED
                + " AND state IN ('CANDIDATE','ACTIVE') AND expires_at>?", Long.class, key(timestamp(now)));
        if (count == null || count < 0) throw unavailable(); return count;
    }

    List<StoredMemory> candidates(Instant now, MemoryKindEnum kind, String after, int limit) {
        if (absent) return List.of();
        var tail = new ArrayList<Object>(); tail.add(timestamp(now));
        String sql = headSql() + " AND e.state='CANDIDATE' AND NOT e.confirmed AND e.expires_at>?";
        if (kind != null) { sql += " AND e.kind=?"; tail.add(kind.name()); }
        if (after != null) { sql += " AND e.memory_id>?"; tail.add(after); }
        sql += " ORDER BY e.memory_id LIMIT ?"; tail.add(limit);
        return jdbc.query(sql, (rs, row) -> stored(rs), headKey(tail.toArray()));
    }

    List<IndexReadiness> indexReadiness(Instant now, MemoryKindEnum kind, MemoryIndexPublication.Binding binding) {
        if (component()) throw unavailable();
        if (absent) return List.of();
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

    Optional<SourceVersionVO> sourceVersion(MemorySourceDTO source, String expectedActorSha256) {
        if (source.sourceRunId() == null) return Optional.empty();
        return single(jdbc.query("""
                SELECT r.subject_type,r.subject_id,e.sequence,e.occurred_at
                FROM tianji_agent.agent_run r JOIN tianji_agent.agent_event e ON e.tenant_id=r.tenant_id AND e.run_id=r.run_id
                WHERE r.tenant_id=? AND r.subject_issuer=? AND r.run_id=? AND r.status='COMPLETED' AND e.event_id=?
                """, (rs, row) -> {
                    var actor = new TrustedAgentActor(tenantId, TrustedAgentActor.SubjectType.valueOf(rs.getString("subject_type")),
                            rs.getString("subject_id"), issuer);
                    return new SourceVersionVO(rs.getLong("sequence"), time(rs, "occurred_at"), actor.identityFingerprint());
                }, tenantId, issuer, source.sourceRunId(), source.sourceEventId()))
                .filter(proof -> proof.actorSha256().equals(expectedActorSha256));
    }

    boolean nativeUpsert(StoredMemory stored) {
        if (!component()) return false;
        var entry = stored.entry();
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM %s.receipt
                WHERE scope_hash=? AND command_namespace='NATIVE' AND operation='UPSERT' AND fact_id=? AND result_revision=?)
                """.formatted(hostSchema), Boolean.class, hostScopeHash, entry.memoryId(), entry.revision()));
    }

    void insert(StoredMemory stored) {
        if (absent) throw unavailable();
        if (component()) { insertComponent(stored); return; }
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
        if (absent) throw unavailable();
        if (component()) { replaceComponent(stored, expectedRevision, redactPrior); return; }
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
        if (fence != null) advanceRevision(stored.entry().updatedAt());
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
        if (absent) return Optional.empty();
        if (component()) return componentReceipt(actor, operation, commandId, requestHash);
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
        if (component()) { recordComponentReceipt(actor, operation, requestHash, result); return; }
        actorScope(actor);
        if (!owner.equals(result.owner()) || !scopeId.equals(result.scopeId())) throw unavailable();
        String epochColumn = fence == null ? "" : ",authority_epoch";
        String epochValue = fence == null ? "" : ",?";
        var values = key(actor.subjectType().name(), actor.subjectId(), operation, result.commandId(), requestHash, result.memoryId(),
                result.revision(), result.outcome().name(), result.stateAtMutation().name(), timestamp(result.occurredAt()));
        if (fence != null) values = prefix(values, new Object[]{authorityEpoch()});
        requireOne(jdbc.update("""
                INSERT INTO tianji_agent.agent_memory_command_receipt
                    (tenant_id,owner_issuer,owner_type,owner_id,scope_id,actor_type,actor_id,operation,command_id,request_sha256,
                     result_memory_id,result_revision,result_outcome,result_state,created_at%s)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?%s)
                """.formatted(epochColumn, epochValue), values));
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
        if (component()) return componentReference(id, revision);
        if (absent) return Optional.empty();
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

    private void insertComponent(StoredMemory stored) {
        var entry = stored.entry();
        entryScope(entry);
        insertComponentRevision(stored);
        requireOne(jdbc.update("""
                INSERT INTO %s.fact(scope_hash,fact_id,fact_type,created_at,current_revision,updated_at)
                VALUES (?,?,?,?,?,?)
                """.formatted(hostSchema), hostScopeHash, entry.memoryId(), entry.kind().name(), timestamp(entry.createdAt()),
                entry.revision(), timestamp(entry.updatedAt())));
    }

    private void replaceComponent(StoredMemory stored, long expectedRevision, boolean redactPrior) {
        var entry = stored.entry();
        entryScope(entry);
        if (redactPrior) {
            int updated = jdbc.update("""
                    UPDATE %s.revision SET state=?,content=NULL,provenance=NULL,redacted_at=?,
                        host_metadata=jsonb_set(host_metadata,'{source}','null'::jsonb)
                    WHERE scope_hash=? AND fact_id=? AND content_profile='HOST_V1'
                    """.formatted(hostSchema), entry.state().name(), timestamp(entry.updatedAt()), hostScopeHash, entry.memoryId());
            if (updated < 1 || countComponentVersions(entry.memoryId()) != updated) throw unavailable();
        } else {
            requireOne(jdbc.update("""
                    UPDATE %s.revision SET state='SUPERSEDED'
                    WHERE scope_hash=? AND fact_id=? AND revision=? AND content_profile='HOST_V1' AND state IN ('CANDIDATE','ACTIVE')
                    """.formatted(hostSchema), hostScopeHash, entry.memoryId(), expectedRevision));
        }
        insertComponentRevision(stored);
        requireOne(jdbc.update("""
                UPDATE %s.fact SET current_revision=?,updated_at=?
                WHERE scope_hash=? AND fact_id=? AND current_revision=? AND fact_type=?
                """.formatted(hostSchema), entry.revision(), timestamp(entry.updatedAt()), hostScopeHash,
                entry.memoryId(), expectedRevision, entry.kind().name()));
    }

    private long countComponentVersions(String id) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM " + hostSchema + ".revision WHERE scope_hash=? AND fact_id=?",
                Long.class, hostScopeHash, id);
        if (count == null) throw unavailable();
        return count;
    }

    private void insertComponentRevision(StoredMemory stored) {
        var entry = stored.entry();
        long sequence = advanceRevision(entry.updatedAt());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", 1);
        metadata.put("source", entry.source() == null ? null : JSON.valueToTree(entry.source()));
        metadata.put("sourceEvidenceSha256", stored.sourceEvidenceSha256());
        metadata.put("sourceActorSha256", stored.sourceActorSha256());
        metadata.put("confidence", entry.confidence().setScale(6).toPlainString());
        metadata.put("sensitivity", entry.sensitivity().name());
        metadata.put("retentionPolicyRef", entry.retentionPolicyRef());
        metadata.put("indexModelRef", stored.indexModelRef());
        metadata.put("supersedesRevision", entry.supersedesRevision());
        metadata.put("reasonCode", stored.reasonCode());
        String json = LegacyMemoryStoreAdapter.json(metadata);
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16 * 1024) throw unavailable();
        if (entry.source() != null) codec.source(entry.source());
        requireOne(jdbc.update("""
                INSERT INTO %s.revision(scope_hash,fact_id,revision,scope_revision,content_profile,content,provenance,state,
                    confirmed,effective_at,expires_at,redacted_at,host_metadata,native_metadata,content_sha256,created_at)
                VALUES (?,?,?,?,'HOST_V1',?::jsonb,NULL,?,?,?,?,?,?::jsonb,NULL,?,?)
                """.formatted(hostSchema), hostScopeHash, entry.memoryId(), entry.revision(), sequence,
                entry.content() == null ? null : codec.content(entry.content()), entry.state().name(), entry.confirmed(),
                timestamp(entry.effectiveAt()), timestamp(entry.expiresAt()), timestamp(stored.retiredAt()), json,
                entry.contentSha256(), timestamp(entry.updatedAt())));
    }

    private void verifyHostMetadata(ResultSet rs) throws SQLException {
        try {
            if (!"HOST_V1".equals(rs.getString("content_profile"))) throw unavailable();
            var metadata = JSON.readTree(rs.getString("host_metadata"));
            var names = new HashSet<String>();
            metadata.fieldNames().forEachRemaining(names::add);
            if (!metadata.isObject() || !names.equals(HOST_METADATA) || !metadata.get("schemaVersion").isIntegralNumber()
                    || metadata.get("schemaVersion").intValue() != 1) throw unavailable();
            for (String name : HOST_METADATA) {
                if (!Set.of("schemaVersion", "source", "supersedesRevision").contains(name) && !metadata.get(name).isTextual()) throw unavailable();
            }
            var source = metadata.get("source");
            var predecessor = metadata.get("supersedesRevision");
            String confidence = metadata.get("confidence").textValue();
            long revision = rs.getLong("current_revision");
            if ((!source.isNull() && !source.isObject())
                    || (!predecessor.isNull() && (!predecessor.isIntegralNumber() || !predecessor.canConvertToLong()
                    || predecessor.longValue() < 1 || predecessor.longValue() >= revision))
                    || !confidence.matches("(?:0|1)(?:\\.[0-9]{1,6})?")
                    || new BigDecimal(confidence).compareTo(BigDecimal.ONE) > 0
                    || source.isNull() != (rs.getString("content_json") == null)) throw unavailable();
            if (JSON.writeValueAsBytes(metadata).length > 16 * 1024) throw unavailable();
        } catch (JsonProcessingException | IllegalArgumentException malformed) {
            throw unavailable();
        }
    }

    private Optional<MemoryMutationResultDTO> componentReceipt(TrustedAgentActor actor, String operation, String commandId, String requestHash) {
        actorScope(actor);
        return single(jdbc.query("""
                SELECT command_receipt.*,r.content_profile FROM %s.receipt command_receipt JOIN %s.revision r
                  ON r.scope_hash=command_receipt.scope_hash AND r.fact_id=command_receipt.fact_id AND r.revision=command_receipt.result_revision
                WHERE command_receipt.scope_hash=? AND command_namespace=? AND command_id=?
                """.formatted(hostSchema, hostSchema), (rs, row) -> {
                    checkReceipt(rs, operation, requestHash);
                    return new MemoryMutationResultDTO(commandId, owner, scopeId, rs.getString("fact_id"), rs.getLong("result_revision"),
                            MemoryMutationOutcomeEnum.valueOf(rs.getString("result_outcome")), MemoryStateEnum.valueOf(rs.getString("result_state")),
                            time(rs, "created_at"));
                }, hostScopeHash, LegacyMemoryStoreAdapter.namespaceOf(actor, operation), commandId));
    }

    private void recordComponentReceipt(TrustedAgentActor actor, String operation, String requestHash, MemoryMutationResultDTO result) {
        actorScope(actor);
        if (!owner.equals(result.owner()) || !scopeId.equals(result.scopeId())) throw unavailable();
        requireOne(jdbc.update("""
                INSERT INTO %s.receipt(scope_hash,command_namespace,command_id,request_sha256,operation,result_outcome,result_state,
                    fact_id,result_revision,authority_epoch,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """.formatted(hostSchema), hostScopeHash, LegacyMemoryStoreAdapter.namespaceOf(actor, operation), result.commandId(),
                requestHash, operation, result.outcome().name(), result.stateAtMutation().name(),
                result.memoryId(), result.revision(), authorityEpoch(), timestamp(result.occurredAt())));
    }

    Optional<MemoryWriteResult> nativeReceipt(String operation, String commandId, String requestHash) {
        if (!component()) throw unavailable();
        return single(jdbc.query("""
                SELECT command_receipt.*,r.content_profile,r.content_sha256 FROM %s.receipt command_receipt JOIN %s.revision r
                  ON r.scope_hash=command_receipt.scope_hash AND r.fact_id=command_receipt.fact_id AND r.revision=command_receipt.result_revision
                WHERE command_receipt.scope_hash=? AND command_namespace='NATIVE' AND command_id=?
                """.formatted(hostSchema, hostSchema), (rs, row) -> {
                    checkReceipt(rs, operation, requestHash);
                    return new MemoryWriteResult(rs.getString("fact_id"), rs.getLong("result_revision"), commandId,
                            rs.getString("content_sha256"), time(rs, "created_at"));
                }, hostScopeHash, commandId));
    }

    MemoryWriteResult recordNativeReceipt(String operation, String commandId, String requestHash, StoredMemory stored) {
        if (!component()) throw unavailable();
        var entry = stored.entry();
        entryScope(entry);
        requireOne(jdbc.update("""
                INSERT INTO %s.receipt(scope_hash,command_namespace,command_id,request_sha256,operation,fact_id,result_revision,
                    authority_epoch,created_at) VALUES (?,'NATIVE',?,?,?,?,?,?,?)
                """.formatted(hostSchema), hostScopeHash, commandId, requestHash, operation, entry.memoryId(),
                entry.revision(), authorityEpoch(), timestamp(entry.updatedAt())));
        return new MemoryWriteResult(entry.memoryId(), entry.revision(), commandId, entry.contentSha256(), entry.updatedAt());
    }

    private static void checkReceipt(ResultSet row, String operation, String requestHash) throws SQLException {
        if (!"HOST_V1".equals(row.getString("content_profile"))) throw unavailable();
        if (!operation.equals(row.getString("operation")) || !requestHash.equals(row.getString("request_sha256"))) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Memory command content changed");
        }
    }

    private Optional<RevisionReference> componentReference(String id, long revision) {
        return single(jdbc.query("""
                SELECT state,content_sha256,host_metadata->>'indexModelRef' AS index_model_ref,created_at,content_profile
                FROM %s.revision WHERE scope_hash=? AND fact_id=? AND revision=?
                """.formatted(hostSchema), (rs, row) -> {
                    if (!"HOST_V1".equals(rs.getString("content_profile"))) throw unavailable();
                    return new RevisionReference(revision, MemoryStateEnum.valueOf(rs.getString("state")), rs.getString("content_sha256"),
                            rs.getString("index_model_ref"), time(rs, "created_at"));
                }, hostScopeHash, id, revision));
    }

    void redactLegacyCopy(StoredMemory retired, MemoryIndexPublication.Binding binding) {
        if (!component()) throw unavailable();
        var entry = retired.entry();
        entryScope(entry);
        String id = entry.memoryId();
        var scopes = jdbc.query("SELECT scope_id FROM tianji_agent.agent_memory_scope WHERE " + OWNED + " FOR UPDATE",
                (rs, row) -> rs.getString(1), key());
        var old = single(jdbc.query(CURRENT + " AND e.memory_id=? FOR UPDATE OF e", (rs, row) -> legacyStored(rs), key(id)));
        assertLegacyIndexRetired(id, binding, old.isEmpty());
        Long revisionCount = jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE " + OWNED
                + " AND memory_id=?", Long.class, key(id));
        if (revisionCount == null) throw unavailable();
        if (old.isEmpty()) {
            if (revisionCount != 0) throw unavailable();
            return;
        }
        if (scopes.size() != 1 || revisionCount < 1) throw unavailable();

        long oldHead = old.orElseThrow().entry().revision();
        int redacted = jdbc.update("""
                UPDATE tianji_agent.agent_memory_revision SET state=?,content_json=NULL,source_json=NULL,redacted_at=?,
                    written_at=CASE WHEN revision=? THEN ? ELSE written_at END WHERE
                """ + OWNED + " AND memory_id=?", prefix(
                        new Object[]{entry.state().name(), timestamp(entry.updatedAt()), oldHead, timestamp(entry.updatedAt())}, key(id)));
        if (redacted != revisionCount) throw unavailable();
        requireOne(jdbc.update("""
                UPDATE tianji_agent.agent_memory_entry SET state=?,retired_at=?,updated_at=? WHERE
                """ + OWNED + " AND memory_id=? AND current_revision=?",
                prefix(new Object[]{entry.state().name(), timestamp(entry.updatedAt()), timestamp(entry.updatedAt())}, key(id, oldHead))));
    }

    void assertLegacyIndexRetired(String id, MemoryIndexPublication.Binding binding, boolean requireNoManifest) {
        if (!component()) throw unavailable();
        var manifests = jdbc.query("SELECT DISTINCT vector_table,index_state FROM tianji_agent.agent_memory_index_manifest WHERE "
                + OWNED + " AND memory_id=?", (rs, row) -> Map.entry(rs.getString(1), rs.getString(2)), key(id));
        Set<String> tables = new HashSet<>(List.of("agent_memory_vector_v1", binding.vectorTable()));
        if (requireNoManifest && !manifests.isEmpty() || manifests.stream().anyMatch(item -> !tables.contains(item.getKey()) || !"DELETED".equals(item.getValue()))) throw unavailable();
        String ownership = LegacyMemoryStoreAdapter.json(Map.of("tenantId", Long.toString(tenantId), "issuerFingerprint", issuer,
                "ownerType", owner.type().name(), "ownerId", owner.id(), "scopeId", scopeId, "memoryId", id));
        var discovered = jdbc.query("""
                SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='tianji_agent' AND c.relkind IN ('r','p') AND c.relname ~ '^agent_memory_vector(_[a-z0-9]+)?$'
                """, (rs, row) -> rs.getString(1));
        if (!tables.equals(Set.copyOf(discovered))) throw unavailable();
        for (var table : tables) {
            if (!Boolean.FALSE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM tianji_agent."%s" physical WHERE physical.metadata @> ?::jsonb
                        UNION ALL
                        SELECT 1 FROM tianji_agent.agent_memory_index_manifest manifest
                        JOIN tianji_agent."%s" physical ON physical.id=manifest.document_id
                        WHERE %s AND memory_id=?
                    )
                    """.formatted(table, table, OWNED), Boolean.class, prefix(new Object[]{ownership}, key(id))))) throw unavailable();
        }
    }

    private static void requireOne(int count) {
        if (count != 1) throw unavailable();
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
        if (component()) verifyHostMetadata(rs);
        return legacyStored(rs);
    }

    private StoredMemory legacyStored(ResultSet rs) throws SQLException {
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
    record SourceVersionVO(long sequence, Instant occurredAt, String actorSha256) {
        SourceVersionVO {
            if (sequence < 1 || occurredAt == null || occurredAt.getNano() % 1_000 != 0) throw unavailable();
        }
    }
    record RevisionReference(long revision, MemoryStateEnum state, String contentSha256, String modelRef, Instant writtenAt) { }
    record Manifest(String memoryId, long revision, String modelRef, UUID documentId, String vectorTable, int dimensions, String contentSha256, String state) { }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory storage is unavailable or changed"); }
}
