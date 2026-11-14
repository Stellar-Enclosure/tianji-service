package top.egon.cola.platform.tianji.process.server.release.draft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftCommandReceiptDTO.OperationEnum;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** The receipt primary key is shared by every draft command for the complete actor. */
@Slf4j
@Validated
public class ProcessDraftCommandReceiptStore {
    private static final Set<String> RESULT_FIELDS = Set.of("draftId", "resultRevision", "resultContentSha256", "releaseVersion");
    private final JdbcTemplate jdbc;
    private final String table;

    /** The explicitly named Process configuration factory supplies an isolated template and exact schema. */
    public ProcessDraftCommandReceiptStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Invalid Process schema");
        this.table = schema + ".tianji_process_draft_command";
    }

    public Optional<ProcessDraftCommandReceiptBO> find(@NotNull ProcessActor actor, @NotNull String commandId) {
        return jdbc.query("""
                SELECT command_id,operation,draft_id,request_sha256,result_json,completed_at
                FROM %s WHERE tenant_id=? AND actor_type=? AND actor_issuer=?::char(64) COLLATE "C" AND actor_id=? AND command_id=?
                """.formatted(table), (row, index) -> read(row, actor), actor.tenantId(), actor.subjectType().name(),
                actor.issuerFingerprint(), actor.subjectId(), commandId).stream().findFirst();
    }

    /** A lost race must leave this transaction before any winning receipt is read. */
    public boolean insertOriginal(@NotNull @Valid ProcessDraftCommandReceiptBO receipt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) {
            throw new IllegalStateException("Draft receipt requires the owning Process transaction");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("draftId", receipt.getDraftId());
        result.put("resultRevision", receipt.getResultRevision());
        result.put("resultContentSha256", receipt.getResultContentSha256());
        result.put("releaseVersion", receipt.getReleaseVersion());
        String json = new String(ProcessTypedJson.canonicalBytes(result, 8192), StandardCharsets.UTF_8);
        ProcessActor actor = receipt.getActor();
        return jdbc.update("""
                INSERT INTO %s (tenant_id,actor_type,actor_issuer,actor_id,command_id,operation,draft_id,request_sha256,result_json,completed_at)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb,?)
                ON CONFLICT (tenant_id,actor_type,actor_issuer,actor_id,command_id) DO NOTHING
                """.formatted(table), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                receipt.getCommandId(), receipt.getOperation().name(), receipt.getDraftId(), receipt.getRequestSha256(),
                json, ProcessDraftStore.stamp(receipt.getCompletedAt())) == 1;
    }

    private static ProcessDraftCommandReceiptBO read(ResultSet row, ProcessActor actor) throws SQLException {
        try {
            var operation = OperationEnum.valueOf(row.getString("operation"));
            var result = ProcessDraftContentCodec.tree(row.getString("result_json").getBytes(StandardCharsets.UTF_8));
            ProcessDraftContentCodec.exact(result, RESULT_FIELDS);
            String draftId = row.getString("draft_id");
            String hash = ProcessDraftContentCodec.string(result.path("resultContentSha256"));
            String release = ProcessDraftContentCodec.nullableString(result.path("releaseVersion"));
            Instant completed = ProcessDraftStore.instant(row, "completed_at");
            if (!draftId.equals(ProcessDraftContentCodec.string(result.path("draftId")))
                    || !hash.matches("[0-9a-f]{64}") || (operation == OperationEnum.PUBLISH) != (release != null)
                    || release != null && !release.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || completed == null || completed.getNano() % 1_000 != 0) throw invalid();
            return ProcessDraftCommandReceiptBO.builder().actor(actor).commandId(row.getString("command_id")).operation(operation)
                    .draftId(draftId).requestSha256(row.getString("request_sha256"))
                    .resultRevision(ProcessDraftContentCodec.revision(result.path("resultRevision"))).resultContentSha256(hash)
                    .releaseVersion(release).completedAt(completed).build();
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Stored draft receipt is invalid");
    }
}
