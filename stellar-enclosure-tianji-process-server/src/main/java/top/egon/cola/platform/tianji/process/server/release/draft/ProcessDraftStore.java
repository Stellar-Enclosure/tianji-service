package top.egon.cola.platform.tianji.process.server.release.draft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO.StateEnum;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The Process database is authoritative for both revision ordering and timestamps. */
@Slf4j
@Validated
public class ProcessDraftStore {
    private static final String OWNER = "tenant_id=? AND owner_type=? AND owner_issuer=?::char(64) COLLATE \"C\" AND owner_id=?";
    private static final String OWNED = OWNER + " AND draft_id=?";
    private static final String SUMMARY = """
            draft_id,definition_key,title,base_release_key,state,revision,content_sha256,published_release_key,created_at,updated_at
            """;
    private static final String DETAIL = SUMMARY + ",base_source_sha256,content_json,published_revision,publish_command_id,published_at,last_command_id";
    private final JdbcTemplate jdbc;
    private final String table;
    private final String releases;

    /** The explicitly named Process configuration factory supplies an isolated template and exact schema. */
    public ProcessDraftStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Invalid Process schema");
        this.table = schema + ".tianji_process_draft";
        this.releases = schema + ".tianji_process_automation_release";
    }

    public Instant now() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT pg_catalog.clock_timestamp()::timestamptz(6)", OffsetDateTime.class)).toInstant();
    }

    public void insert(@NotNull @Valid ProcessDraftBO draft, @NotNull String canonicalContent) {
        ProcessActor owner = draft.getOwner();
        int written = jdbc.update("""
                INSERT INTO %s (tenant_id,draft_id,definition_key,title,owner_type,owner_issuer,owner_id,base_release_key,
                  base_source_sha256,state,revision,content_json,content_sha256,last_command_id,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?, ?,?,'EDITING',1,?::jsonb,?,?,?,?)
                """.formatted(table), owner.tenantId(), draft.getDraftId(), draft.getDefinitionKey(), draft.getTitle(),
                owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), draft.getBaseReleaseVersion(),
                draft.getBaseSourceManifestSha256(), canonicalContent, draft.getContentSha256(), draft.getLastCommandId(),
                stamp(draft.getCreatedAt()), stamp(draft.getUpdatedAt()));
        if (written != 1) throw new IllegalStateException("Draft insert did not write one row");
    }

    public Optional<ProcessDraftBO> get(@NotNull ProcessActor owner, @NotNull String draftId,
                                        @NotNull ReadLockEnum lock) {
        String sql = "SELECT " + DETAIL + " FROM " + table + " WHERE " + OWNED + lock.sql;
        return jdbc.query(sql, (row, index) -> detail(row, owner), owned(owner, draftId)).stream().findFirst();
    }

    public Optional<ProcessDraftBO> summary(@NotNull ProcessActor owner, @NotNull String draftId) {
        return jdbc.query("SELECT " + SUMMARY + " FROM " + table + " WHERE " + OWNED,
                (row, index) -> summary(row, owner), owned(owner, draftId)).stream().findFirst();
    }

    public List<ProcessDraftBO> list(@NotNull ProcessActor owner, String definitionKey, @NotNull StateEnum state,
                                    @NotNull Instant ceiling, Instant beforeCreatedAt, String beforeId, int limit) {
        var sql = new StringBuilder("SELECT ").append(SUMMARY).append(" FROM ").append(table)
                .append(" WHERE ").append(OWNER).append(" AND state=? AND created_at<=?");
        var arguments = new ArrayList<Object>(Arrays.asList(actor(owner)));
        arguments.add(state.name());
        arguments.add(stamp(ceiling));
        if (definitionKey != null) {
            sql.append(" AND definition_key=?");
            arguments.add(definitionKey);
        }
        if (beforeCreatedAt != null) {
            sql.append(" AND (created_at,draft_id)<(?,?)");
            arguments.add(stamp(beforeCreatedAt));
            arguments.add(beforeId);
        }
        sql.append(" ORDER BY created_at DESC,draft_id DESC LIMIT ?");
        arguments.add(limit);
        return jdbc.query(sql.toString(), (row, index) -> summary(row, owner), arguments.toArray());
    }

    public int save(@NotNull ProcessActor owner, @NotNull String draftId, long expected, @NotNull String title,
                    @NotNull String canonicalContent, @NotNull String hash, @NotNull String commandId, @NotNull Instant now) {
        return jdbc.update("UPDATE " + table + " SET title=?,content_json=?::jsonb,content_sha256=?,revision=revision+1,"
                        + " last_command_id=?,updated_at=? WHERE " + OWNED + " AND state='EDITING' AND revision=?",
                title, canonicalContent, hash, commandId, stamp(now), owner.tenantId(), owner.subjectType().name(),
                owner.issuerFingerprint(), owner.subjectId(), draftId, expected);
    }

    public int discard(@NotNull ProcessActor owner, @NotNull String draftId, long expected, @NotNull String commandId,
                       @NotNull Instant now) {
        return jdbc.update("UPDATE " + table + " SET state='DISCARDED',revision=revision+1,last_command_id=?,updated_at=? WHERE "
                        + OWNED + " AND state='EDITING' AND revision=?", commandId, stamp(now), owner.tenantId(),
                owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), draftId, expected);
    }

    private ProcessDraftBO detail(ResultSet row, ProcessActor owner) throws SQLException {
        var draft = summary(row, owner).setBaseSourceManifestSha256(row.getString("base_source_sha256"))
                .setContent(ProcessDraftContentCodec.decodeStored(row.getString("content_json")))
                .setPublishedRevision(row.getObject("published_revision", Long.class))
                .setPublishCommandId(row.getString("publish_command_id")).setPublishedAt(instant(row, "published_at"))
                .setLastCommandId(row.getString("last_command_id"));
        if (draft.getState() == StateEnum.PUBLISHED) {
            var publication = jdbc.query("""
                    SELECT source_sha256,compiled_sha256,dependency_sha256,compiler_version
                    FROM %s WHERE tenant_id=? AND release_key=? AND root_definition_key=? AND source_draft_id=?
                      AND source_draft_revision=? AND draft_content_sha256=?::char(64) COLLATE "C"
                    """.formatted(releases), (fields, index) -> draft.setPublishedSourceSha256(fields.getString("source_sha256"))
                            .setPublishedCompiledSha256(fields.getString("compiled_sha256"))
                            .setPublishedDependencySha256(fields.getString("dependency_sha256"))
                            .setPublishedCompilerVersion(fields.getString("compiler_version")),
                    owner.tenantId(), draft.getPublishedReleaseVersion(), draft.getDefinitionKey(), draft.getDraftId(),
                    draft.getPublishedRevision(), draft.getContentSha256());
            if (publication.size() != 1) throw new IllegalStateException("Stored draft publication evidence is unavailable");
        }
        return draft;
    }

    private static ProcessDraftBO summary(ResultSet row, ProcessActor owner) throws SQLException {
        return ProcessDraftBO.builder().owner(owner).draftId(row.getString("draft_id")).definitionKey(row.getString("definition_key"))
                .title(row.getString("title")).baseReleaseVersion(row.getString("base_release_key"))
                .state(StateEnum.valueOf(row.getString("state"))).revision(row.getLong("revision")).contentSha256(row.getString("content_sha256"))
                .publishedReleaseVersion(row.getString("published_release_key")).createdAt(instant(row, "created_at"))
                .updatedAt(instant(row, "updated_at")).build();
    }

    static OffsetDateTime stamp(Instant value) { return value.atOffset(ZoneOffset.UTC); }

    static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime timestamp = row.getObject(column, OffsetDateTime.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Object[] actor(ProcessActor owner) {
        return new Object[]{owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId()};
    }

    private static Object[] owned(ProcessActor owner, String draftId) {
        return new Object[]{owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), draftId};
    }

    public enum ReadLockEnum {
        NONE(""), SHARE(" FOR SHARE"), UPDATE(" FOR UPDATE");

        private final String sql;
        ReadLockEnum(String sql) { this.sql = sql; }
    }
}
