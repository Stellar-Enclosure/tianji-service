package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;

/** Read-only startup check for the pre-provisioned Agent ledger and session schema. */
public final class AgentStorageVerifier {
    private static final Map<String, String> MIGRATIONS = Map.of(
            "1", "V1__create_agent_ledger.sql",
            "2", "V2__add_run_governance.sql",
            "3", "V3__add_adk_session_store.sql",
            "4", "V4__add_adk_session_codec_versions.sql",
            "5", "V5__bind_agent_ownership_to_issuer.sql",
            "6", "V6__add_execution_grant_evidence.sql");
    private static final Map<String, String> COLUMNS = Map.ofEntries(
            Map.entry("agent_definition", "tenant_id, definition_key, active_version, created_at"),
            Map.entry("agent_definition_version", "tenant_id, definition_key, version, content_sha256, specification, created_at"),
            Map.entry("agent_run", "tenant_id, run_id, definition_key, definition_version, subject_type, subject_issuer, subject_id, idempotency_key, request_hash, session_id, correlation_id, status, wait_reason, cancel_requested_at, revision, last_event_sequence, attempt, lease_epoch, lease_owner, lease_expires_at, deadline_at, execution_snapshot, result, error_code, created_at, finished_at, model_calls_reserved, tool_calls_reserved, active_model_calls, active_tool_calls, input_tokens_reserved, output_tokens_reserved, estimated_cost_reserved"),
            Map.entry("agent_event", "tenant_id, run_id, sequence, event_id, event_type, schema_version, author, branch, tool_call_id, correlation_id, native_event_id, payload, occurred_at"),
            Map.entry("agent_run_command_receipt", "tenant_id, subject_type, subject_issuer, subject_id, command_id, operation, request_hash, run_id, result, created_at"),
            Map.entry("agent_budget_reservation", "tenant_id, run_id, reservation_id, lease_epoch, kind, tool_version, input_tokens, output_tokens, estimated_cost, state, actual_input_tokens, actual_output_tokens, actual_cost, created_at, settled_at"),
            Map.entry("agent_session", "tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, session_state, state_codec_version, revision, last_event_sequence, created_at, updated_at"),
            Map.entry("agent_session_event", "tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, sequence, native_event_id, event_timestamp_ms, payload"),
            Map.entry("agent_session_scope_state", "tenant_id, app_name, scope_type, subject_type, subject_issuer, subject_id, state_key, state_value, value_codec_version"),
            Map.entry("agent_session_lease", "tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, lease_epoch, lease_owner, lease_expires_at"),
            Map.entry("agent_run_execution_grant", "tenant_id, run_id, binding_sha256, issue_epoch, grant_id, ciphertext, expires_at, updated_at"));
    private static final List<String> SESSION_OWNER = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id", "app_name", "session_id");
    private static final List<String> RUN_KEY = List.of("tenant_id", "run_id");
    private static final String MODEL_USAGE_FILE = "V15__add_agent_model_usage.sql";
    private static final String MODEL_USAGE_SHA256 = "f5b414af7c1b422697b7ab98823d78fbff0f6327e858a1f5bf816e6b75a74238";
    private static final String CHECKPOINT_FILE = "V16__add_agent_checkpoint.sql";
    private static final String CHECKPOINT_SHA256 = "8521cd49e7ae4401f7dd9847b27918eeeca26b2ef926aa3d43821d900ed2ca36";
    private static final String CHECKPOINT_TABLE = "agent_checkpoint";
    private static final List<String> CHECKPOINT_KEY = List.of("tenant_id", "run_id", "attempt", "checkpoint_no");
    private static final List<String> CHECKPOINT_SESSION_KEY = append(SESSION_OWNER, "session_event_sequence");
    private static final List<String> MODEL_USAGE_KEY = List.of("tenant_id", "run_id", "reservation_id");
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public AgentStorageVerifier(DataSource source) {
        if (source == null) throw new IllegalArgumentException("Agent DataSource is required");
        dataSource = source;
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** Does not create, alter or repair schema objects; migration remains an operator action. */
    public void verify() {
        try {
            transaction.executeWithoutResult(status -> {
                Boolean primary = jdbc.queryForObject(
                        "SELECT NOT pg_is_in_recovery() AND current_setting('transaction_read_only') = 'off'", Boolean.class);
                if (!Boolean.TRUE.equals(primary)) throw invalid();
                verifyHistory();
                verifyColumns();
                verifyIdentityColumns();
                verifyConstraints();
                verifyNativeEventIndex();
            });
        } catch (DataAccessException failure) {
            throw invalid();
        }
    }

    /** Linked process submissions additionally require the separately provisioned V7 fence. */
    public void verifyLinked() {
        verify();
        try {
            transaction.executeWithoutResult(status -> {
                int expected = checksum("V7__add_process_dispatch_fence.sql");
                List<Boolean> history = jdbc.query("SELECT success,checksum FROM tianji_agent.flyway_schema_history "
                                + "WHERE version='7' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                                && rs.getObject("checksum") != null && rs.getInt("checksum") == expected);
                if (history.size() != 1 || !history.getFirst()) throw invalid();
                verifyLinkedColumns();
                Set<Key> constraints = Set.of(
                        key("agent_process_dispatch", "p", "tenant_id", "subject_issuer", "subject_id", "idempotency_key"),
                        key("agent_process_dispatch", "u", "tenant_id", "source_id", "scope_type", "scope_id", "occurrence_id"),
                        key("agent_process_dispatch", "u", "tenant_id", "run_id"),
                        foreign("agent_process_dispatch", RUN_KEY, "agent_run", RUN_KEY, "a"));
                if (!readConstraints().containsAll(constraints)) throw invalid();
                verifyLinkedChecks();
                String table = "tianji_agent.agent_process_dispatch";
                Boolean permitted = jdbc.queryForObject("SELECT has_table_privilege(current_user, ?, 'SELECT') "
                        + "AND has_table_privilege(current_user, ?, 'INSERT') AND has_table_privilege(current_user, ?, 'UPDATE')",
                        Boolean.class, table, table, table);
                if (!Boolean.TRUE.equals(permitted)) throw invalid();
            });
        } catch (DataAccessException failure) {
            throw invalid();
        }
    }

    /** SERVICE execution additionally requires the separately provisioned V8 grant store. */
    public void verifyServiceGrants() {
        verifyLinked();
        try {
            transaction.executeWithoutResult(status -> {
                int expected = checksum("V8__add_service_execution_grant.sql");
                List<Boolean> history = jdbc.query("SELECT success,checksum FROM tianji_agent.flyway_schema_history "
                                + "WHERE version='8' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                                && rs.getObject("checksum") != null && rs.getInt("checksum") == expected);
                if (history.size() != 1 || !history.getFirst()) throw invalid();
                verifyServiceGrantColumns();
                Set<Key> constraints = Set.of(key("agent_run_service_execution_grant", "p", "tenant_id", "run_id"),
                        foreign("agent_run_service_execution_grant", RUN_KEY, "agent_process_dispatch", RUN_KEY, "c"));
                if (!readConstraints().containsAll(constraints)) throw invalid();
                Map<String, String> checks = new HashMap<>();
                jdbc.query("""
                        SELECT c.conname, c.convalidated, pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c
                        JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                        WHERE n.nspname='tianji_agent' AND t.relname='agent_run_service_execution_grant' AND c.contype='c'
                        """, rs -> {
                    if (!rs.getBoolean("convalidated")) throw invalid();
                    checks.put(rs.getString("conname"), normalizedCheck(rs.getString("definition")));
                });
                Map<String, String> expectedChecks = Map.of(
                        "agent_service_grant_tenant_positive", "CHECKtenant_id>0",
                        "agent_service_grant_binding_hash", "CHECKbinding_sha256~'^[0-9a-f]{64}$'",
                        "agent_service_grant_epoch_positive", "CHECKissue_epoch>0",
                        "agent_service_grant_id_format", "CHECKgrant_idISNULLORgrant_id~'^8[0-9]{62}$'",
                        "agent_service_grant_cipher_format", "CHECKciphertextISNULLORciphertext~~'ENC:v1:%'",
                        "agent_service_grant_cipher_size", "CHECKciphertextISNULLORoctet_lengthciphertext<=32768",
                        "agent_service_grant_cipher_presence", "CHECKgrant_idISNULL=ciphertextISNULL",
                        "agent_service_grant_expiry_presence", "CHECKgrant_idISNULL=expires_atISNULL");
                for (var entry : expectedChecks.entrySet()) if (!entry.getValue().equals(checks.get(entry.getKey()))) throw invalid();
                String table = "tianji_agent.agent_run_service_execution_grant";
                Boolean permitted = jdbc.queryForObject("SELECT has_table_privilege(current_user, ?, 'SELECT') "
                        + "AND has_table_privilege(current_user, ?, 'INSERT') AND has_table_privilege(current_user, ?, 'UPDATE')",
                        Boolean.class, table, table, table);
                if (!Boolean.TRUE.equals(permitted)) throw invalid();
            });
        } catch (DataAccessException failure) {
            throw invalid();
        }
    }

    /** Optional model-context capture requires the complete migrated history but no additional secret-table grants. */
    public void verifyContextSnapshots() {
        verify();
        try {
            transaction.executeWithoutResult(status -> {
                Map.of("7", "V7__add_process_dispatch_fence.sql", "8", "V8__add_service_execution_grant.sql", "9", "V9__add_context_snapshots.sql")
                        .forEach((version, file) -> {
                            int expected = checksum(file);
                            var history = jdbc.query("SELECT success,checksum FROM tianji_agent.flyway_schema_history WHERE version=? AND type='SQL'",
                                    (rs, row) -> rs.getBoolean("success") && rs.getObject("checksum") != null && rs.getInt("checksum") == expected, version);
                            if (history.size() != 1 || !history.getFirst()) throw invalid();
                        });
                verifyContextColumns();
                var keys = List.of("tenant_id", "run_id", "reservation_id");
                if (!readConstraints().containsAll(Set.of(key("agent_context_snapshot", "p", keys),
                        key("agent_context_snapshot", "u", "tenant_id", "run_id", "model_call_number"),
                        foreign("agent_context_snapshot", keys, "agent_budget_reservation", keys, "c")))) throw invalid();
                Map<String, String> checks = new HashMap<>();
                jdbc.query("""
                        SELECT c.conname,c.convalidated,pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c
                        JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                        WHERE n.nspname='tianji_agent' AND t.relname='agent_context_snapshot' AND c.contype='c'
                        """, rs -> {
                    if (!rs.getBoolean("convalidated")) throw invalid();
                    checks.put(rs.getString("conname"), normalizedCheck(rs.getString("definition")));
                });
                for (var entry : contextChecks().entrySet()) if (!entry.getValue().equals(checks.get(entry.getKey()))) throw invalid();
                String table = "tianji_agent.agent_context_snapshot";
                if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT has_table_privilege(current_user,?,'SELECT') "
                        + "AND has_table_privilege(current_user,?,'INSERT')", Boolean.class, table, table))) throw invalid();
            });
        } catch (DataAccessException failure) { throw invalid(); }
    }

    /** Validates the optional V16 capture store without installing it or reading checkpoint data. */
    public void verifyCheckpointCapture() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw invalid();
        String migration = pinnedMigration(CHECKPOINT_FILE, CHECKPOINT_SHA256);
        verify();
        try {
            transaction.executeWithoutResult(status -> {
                verifyCheckpointPrimary("off");
                verifyCheckpointCatalog(migration, false);
            });
        } catch (DataAccessException failure) {
            throw invalid();
        }
    }

    /** Verifies V16 for an independently provisioned SELECT-only checkpoint reader. */
    public void verifyCheckpointObservation() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(dataSource)) throw invalid();
        String migration = pinnedMigration(CHECKPOINT_FILE, CHECKPOINT_SHA256);
        var observation = new TransactionTemplate(transaction.getTransactionManager());
        observation.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        observation.setTimeout(2);
        try {
            observation.executeWithoutResult(status -> {
                jdbc.execute("SET TRANSACTION READ ONLY");
                jdbc.execute("SET LOCAL statement_timeout = '2s'");
                jdbc.execute("SET LOCAL search_path = pg_catalog, tianji_agent");
                verifyCheckpointPrimary("on");
                if (!checkpointObservationCoreTables()) throw invalid();
                verifyCheckpointCatalog(migration, true);
            });
        } catch (DataAccessException | org.springframework.transaction.TransactionException failure) {
            throw invalid();
        }
    }

    /** Rechecks the effective V16 reader role on the caller's exact primary transaction. */
    public boolean checkpointObservationRoleReadable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(dataSource)) return false;
        try {
            verifyCheckpointPrimary("on");
            verifyCheckpointRole(true);
            return checkpointObservationCoreTables();
        } catch (DataAccessException | AgentLedgerException failure) {
            return false;
        }
    }

    private boolean checkpointObservationCoreTables() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)=9 FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='tianji_agent'
                  AND c.relname IN ('agent_run','agent_definition_version','agent_event','agent_session',
                      'agent_session_lease','agent_session_event','agent_session_scope_state',
                      'agent_budget_reservation','flyway_schema_history')
                  AND c.relkind='r' AND c.relpersistence='p' AND NOT c.relispartition
                  AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity
                  AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_inherits
                                  WHERE inhrelid=c.oid OR inhparent=c.oid)
                """, Boolean.class));
    }

    /** Checks exact V16 read and V13 evidence access on a bound writable takeover transaction. */
    public boolean checkpointTakeoverEvidenceReadable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(dataSource)) return false;
        try {
            verifyCheckpointPrimary("off");
            verifyCheckpointRole(true);
            return checkpointObservationCoreTables() && checkpointToolEvidenceReadable("off");
        } catch (DataAccessException | AgentLedgerException failure) {
            return false;
        }
    }

    /** Confirms the V13 evidence relations in the caller's already-bound physical primary read transaction. */
    public boolean checkpointToolEvidenceReadable() {
        return checkpointToolEvidenceReadable("on");
    }

    private boolean checkpointToolEvidenceReadable(String readOnly) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(dataSource)) return false;
        try {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT NOT pg_catalog.pg_is_in_recovery()
                      AND pg_catalog.current_setting('transaction_read_only')=?
                      AND (SELECT count(*)=2 FROM pg_catalog.pg_class c
                        JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                        WHERE n.nspname='tianji_agent' AND c.relname IN ('agent_tool_invocation','agent_tool_action_intent')
                          AND c.relkind='r' AND c.relpersistence='p' AND NOT c.relispartition
                          AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity
                          AND pg_catalog.has_schema_privilege(n.oid,'USAGE')
                          AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_inherits
                                          WHERE inhrelid=c.oid OR inhparent=c.oid)
                          AND (SELECT count(*)=2 AND bool_and(pg_catalog.has_column_privilege(c.oid,a.attnum,'SELECT'))
                               FROM pg_catalog.pg_attribute a
                               WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
                                 AND a.attname IN ('tenant_id',CASE c.relname WHEN 'agent_tool_invocation'
                                                       THEN 'run_id' ELSE 'origin_run_id' END)))
                    """, Boolean.class, readOnly))) return false;
            String script = ToolExecutionStorageVerifier.FILE;
            int expected = checksum(script);
            var history = jdbc.query("""
                    SELECT type,script,success,checksum FROM ONLY tianji_agent.flyway_schema_history
                    WHERE version='13' LIMIT 2
                    """, (rs, row) -> "SQL".equals(rs.getString("type"))
                    && script.equals(rs.getString("script")) && rs.getBoolean("success")
                    && rs.getObject("checksum") != null && rs.getInt("checksum") == expected);
            return history.size() == 1 && history.getFirst();
        } catch (DataAccessException failure) {
            return false;
        }
    }

    private void verifyCheckpointPrimary(String readOnly) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT pg_is_in_recovery() AND current_setting('transaction_read_only')=?
                  AND current_setting('server_encoding')='UTF8'
                  AND current_setting('session_replication_role')='origin'
                  AND current_setting('standard_conforming_strings')='on'
                """, Boolean.class, readOnly))) throw invalid();
    }

    private void verifyCheckpointCatalog(String migration, boolean observation) {
        int expectedChecksum = checksum(CHECKPOINT_FILE);
        var history = jdbc.query("""
                SELECT type,script,success,checksum FROM ONLY tianji_agent.flyway_schema_history
                WHERE version='16' LIMIT 2
                """, (rs, row) -> "SQL".equals(rs.getString("type"))
                && CHECKPOINT_FILE.equals(rs.getString("script")) && rs.getBoolean("success")
                && rs.getObject("checksum") != null && rs.getInt("checksum") == expectedChecksum);
        if (history.size() != 1 || !history.getFirst()) throw invalid();
        verifyCheckpointColumns();
        verifyCheckpointKeysAndIndexes();
        verifyOptionalChecks(CHECKPOINT_TABLE, migration, 9, AgentStorageVerifier::normalizedCheck);
        verifyCheckpointRole(observation);
    }

    private void verifyCheckpointColumns() {
        var expected = new HashMap<String, UsageColumn>();
        for (String name : List.of("tenant_id", "run_lease_epoch", "run_revision", "run_event_sequence",
                "session_lease_epoch", "prior_session_revision", "session_revision", "prior_session_event_sequence",
                "session_event_sequence", "definition_version")) {
            expected.put(name, new UsageColumn("bigint", true, false, null));
        }
        for (String name : List.of("attempt", "checkpoint_no")) {
            expected.put(name, new UsageColumn("integer", true, false, null));
        }
        Map.ofEntries(Map.entry("run_id", 64), Map.entry("subject_type", 16), Map.entry("subject_issuer", 64),
                Map.entry("subject_id", 256), Map.entry("app_name", 128), Map.entry("session_id", 128),
                Map.entry("native_invocation_id", 128), Map.entry("native_event_id", 128), Map.entry("definition_key", 128),
                Map.entry("policy_version_ref", 256), Map.entry("engine_version", 32), Map.entry("safe_boundary", 64))
                .forEach((name, length) -> expected.put(name, new UsageColumn("character varying(" + length + ")", true, true, null)));
        for (String name : List.of("definition_sha256", "request_sha256", "snapshot_sha256")) {
            expected.put(name, new UsageColumn("character(64)", true, true, null));
        }
        expected.put("tool_receipts", new UsageColumn("jsonb", true, false, null));
        expected.put("captured_at", new UsageColumn("timestamp(6) with time zone", true, false, null));
        if (!expected.equals(readOptionalColumns(CHECKPOINT_TABLE))) throw invalid();
    }

    private void verifyCheckpointKeysAndIndexes() {
        Set<Key> expected = Set.of(key(CHECKPOINT_TABLE, "p", CHECKPOINT_KEY),
                foreign(CHECKPOINT_TABLE, RUN_KEY, "agent_run", RUN_KEY, "c"),
                foreign(CHECKPOINT_TABLE, CHECKPOINT_SESSION_KEY, "agent_session_event", append(SESSION_OWNER, "sequence"), "c"));
        var actual = readConstraints();
        actual.removeIf(key -> !CHECKPOINT_TABLE.equals(key.table()));
        if (!expected.equals(actual)) throw invalid();

        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)=3 AND bool_and(c.convalidated AND NOT c.condeferrable AND NOT c.condeferred
                    AND (c.contype='p' OR (c.contype='f' AND c.confupdtype='a' AND c.confdeltype='c' AND c.confmatchtype='s')))
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname='agent_checkpoint' AND c.contype NOT IN ('c','n')
                """, Boolean.class))) throw invalid();

        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)=2 AND bool_and(i.indisvalid AND i.indisready AND i.indislive AND i.indimmediate
                    AND am.amname='btree' AND i.indpred IS NULL AND i.indexprs IS NULL AND i.indnkeyatts=i.indnatts
                    AND NOT EXISTS (SELECT 1 FROM unnest(i.indoption::smallint[]) v(options) WHERE options<>0)
                    AND NOT EXISTS (SELECT 1 FROM unnest(i.indclass::oid[]) k(oid)
                                    JOIN pg_opclass op ON op.oid=k.oid WHERE NOT op.opcdefault)
                    AND NOT EXISTS (SELECT 1 FROM unnest(i.indcollation::oid[]) WITH ORDINALITY co(oid,pos)
                                    JOIN unnest(i.indkey::smallint[]) WITH ORDINALITY k(num,pos) USING(pos)
                                    JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num
                                    WHERE co.oid<>a.attcollation)
                    AND CASE x.relname WHEN 'agent_checkpoint_pkey'
                          THEN i.indisprimary AND i.indisunique AND i.indnatts=4
                        WHEN 'agent_checkpoint_session_event_idx'
                          THEN NOT i.indisprimary AND NOT i.indisunique AND i.indnatts=7
                            AND ARRAY(SELECT a.attname::text FROM unnest(i.indkey::smallint[]) WITH ORDINALITY k(num,pos)
                                      JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num ORDER BY k.pos)
                                = ARRAY['tenant_id','subject_type','subject_issuer','subject_id','app_name','session_id','session_event_sequence']
                        ELSE false END)
                FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_class x ON x.oid=i.indexrelid JOIN pg_am am ON am.oid=x.relam
                WHERE n.nspname='tianji_agent' AND t.relname='agent_checkpoint'
                """, Boolean.class))) throw invalid();
    }

    private void verifyCheckpointRole(boolean observation) {
        Boolean permitted = jdbc.queryForObject("""
                SELECT c.relpersistence='p' AND NOT c.relispartition AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity
                  AND has_schema_privilege(n.oid,'USAGE') AND NOT has_schema_privilege(n.oid,'CREATE')
                  AND NOT EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname=current_user AND (r.rolsuper OR r.rolbypassrls))
                  AND NOT pg_has_role(c.relowner,'MEMBER') AND NOT pg_has_role(n.nspowner,'MEMBER')
                  AND NOT has_table_privilege(c.oid,'DELETE,TRUNCATE,TRIGGER')
                  AND CASE WHEN ? THEN
                        has_table_privilege(c.oid,'SELECT')
                        AND NOT has_any_column_privilege(c.oid,'INSERT,UPDATE,REFERENCES,SELECT WITH GRANT OPTION')
                      ELSE has_table_privilege(c.oid,'INSERT')
                        AND NOT has_any_column_privilege(c.oid,'SELECT,UPDATE,REFERENCES,INSERT WITH GRANT OPTION') END
                  AND CASE WHEN current_setting('server_version_num')::integer >= 170000
                        THEN NOT has_table_privilege(c.oid,'MAINTAIN') ELSE true END
                  AND NOT EXISTS (SELECT 1 FROM pg_inherits h WHERE h.inhrelid=c.oid OR h.inhparent=c.oid)
                  AND NOT EXISTS (SELECT 1 FROM pg_trigger tr WHERE tr.tgrelid=c.oid
                    AND (NOT tr.tgisinternal OR tr.tgenabled<>'O'))
                  AND (SELECT count(*)=2 AND bool_and(
                         (SELECT count(*)=4 AND bool_and(tr.tgisinternal AND tr.tgenabled='O')
                          FROM pg_trigger tr WHERE tr.tgconstraint=fk.oid))
                       FROM pg_constraint fk WHERE fk.conrelid=c.oid AND fk.contype='f')
                  AND NOT EXISTS (SELECT 1 FROM pg_rewrite rule WHERE rule.ev_class=c.oid)
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='tianji_agent' AND c.relname='agent_checkpoint' AND c.relkind='r'
                """, Boolean.class, observation);
        if (!Boolean.TRUE.equals(permitted)) throw invalid();
    }

    /** Checks the optional V15 store on the owning primary, without installing or repairing it. */
    public void verifyModelUsage() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw invalid();
        String migration = pinnedMigration(MODEL_USAGE_FILE, MODEL_USAGE_SHA256);
        verify();
        try {
            transaction.executeWithoutResult(status -> {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT NOT pg_is_in_recovery() AND current_setting('transaction_read_only')='off'
                          AND current_setting('server_encoding')='UTF8'
                          AND current_setting('session_replication_role')='origin'
                        """, Boolean.class))) throw invalid();
                int expectedChecksum = checksum(MODEL_USAGE_FILE);
                var history = jdbc.query("""
                        SELECT type,script,success,checksum FROM tianji_agent.flyway_schema_history
                        WHERE version='15'
                        """, (rs, row) -> "SQL".equals(rs.getString("type"))
                        && MODEL_USAGE_FILE.equals(rs.getString("script")) && rs.getBoolean("success")
                        && rs.getObject("checksum") != null && rs.getInt("checksum") == expectedChecksum);
                if (history.size() != 1 || !history.getFirst()) throw invalid();
                verifyModelUsageColumns();
                verifyModelUsageKeys();
                verifyOptionalChecks("agent_model_usage", migration, 8, AgentStorageVerifier::modelUsageCheckSignature);
                verifyModelUsageRole();
            });
        } catch (DataAccessException failure) {
            throw invalid();
        }
    }

    private void verifyModelUsageColumns() {
        Map<String, UsageColumn> expected = Map.ofEntries(
                usageColumn("tenant_id", "bigint", true, false),
                usageColumn("run_id", "character varying(64)", true, false),
                usageColumn("reservation_id", "character varying(128)", true, true),
                usageColumn("lease_epoch", "bigint", true, false),
                usageColumn("model_route_ref", "character varying(256)", true, true),
                usageColumn("model_name", "character varying(512)", true, true),
                usageColumn("price_version_ref", "character(64)", false, true),
                usageColumn("currency", "character(3)", false, true),
                usageColumn("input_rate_per_million", "numeric(32,8)", false, false),
                usageColumn("output_rate_per_million", "numeric(32,8)", false, false),
                Map.entry("state", new UsageColumn("character varying(16)", true, true, "'PENDING'")),
                usageColumn("actual_input_tokens", "bigint", false, false),
                usageColumn("actual_output_tokens", "bigint", false, false),
                usageColumn("actual_cost", "numeric(24,8)", false, false),
                usageColumn("safe_reason", "character varying(32)", false, true),
                usageColumn("final_sha256", "character(64)", false, true),
                usageColumn("created_at", "timestamp(6) with time zone", true, false),
                usageColumn("measured_at", "timestamp(6) with time zone", false, false));
        if (!expected.equals(readOptionalColumns("agent_model_usage"))) throw invalid();
    }

    private Map<String, UsageColumn> readOptionalColumns(String table) {
        Map<String, UsageColumn> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull,a.attidentity,a.attgenerated,
                       co.collname,cn.nspname AS collation_schema,pg_get_expr(d.adbin,d.adrelid) AS default_value
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                LEFT JOIN pg_namespace cn ON cn.oid=co.collnamespace
                LEFT JOIN pg_attrdef d ON d.adrelid=t.oid AND d.adnum=a.attnum
                WHERE n.nspname='tianji_agent' AND t.relname=? AND t.relkind='r'
                """, rs -> {
            String collation = rs.getString("collname");
            String defaultValue = rs.getString("default_value");
            if (!"".equals(rs.getString("attidentity")) || !"".equals(rs.getString("attgenerated"))
                    || "C".equals(collation) && !"pg_catalog".equals(rs.getString("collation_schema"))
                    || collation != null && !"C".equals(collation) && !"default".equals(collation)) throw invalid();
            var entry = new UsageColumn(rs.getString("type"), rs.getBoolean("attnotnull"),
                    "C".equals(collation), defaultValue == null ? null : normalizedCheck(defaultValue));
            if (actual.put(rs.getString("attname"), entry) != null) throw invalid();
        }, table);
        return actual;
    }

    private void verifyModelUsageKeys() {
        Set<String> seen = new HashSet<>();
        jdbc.query("""
                SELECT c.conname,c.contype,c.convalidated,c.condeferrable,c.condeferred,
                       c.confupdtype,c.confdeltype,c.confmatchtype,rn.nspname AS ref_schema,rt.relname AS ref_table,
                       i.indisprimary,i.indisunique,i.indisvalid,i.indisready,i.indislive,i.indimmediate,
                       i.indnkeyatts,i.indnatts,x.relname AS index_name,am.amname,
                       i.indpred IS NULL AND i.indexprs IS NULL AS plain_index,
                       NOT EXISTS (SELECT 1 FROM unnest(i.indoption::smallint[]) v(options) WHERE options<>0) AS ascending,
                       NOT EXISTS (SELECT 1 FROM unnest(i.indclass::oid[]) k(oid)
                                    JOIN pg_opclass op ON op.oid=k.oid WHERE NOT op.opcdefault) AS default_classes,
                       NOT EXISTS (SELECT 1 FROM unnest(i.indcollation::oid[]) WITH ORDINALITY co(oid,pos)
                                    JOIN unnest(i.indkey::smallint[]) WITH ORDINALITY k(num,pos) USING(pos)
                                    JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num
                                    WHERE co.oid<>a.attcollation) AS column_collations,
                       (SELECT count(*) FROM pg_index j WHERE j.indrelid=t.oid) AS index_count,
                       ARRAY(SELECT a.attname::text FROM unnest(c.conkey) WITH ORDINALITY k(num,pos)
                             JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num ORDER BY k.pos) AS columns,
                       ARRAY(SELECT a.attname::text FROM unnest(c.confkey) WITH ORDINALITY k(num,pos)
                             JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num ORDER BY k.pos) AS ref_columns
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_class rt ON rt.oid=c.confrelid LEFT JOIN pg_namespace rn ON rn.oid=rt.relnamespace
                LEFT JOIN pg_index i ON i.indexrelid=c.conindid LEFT JOIN pg_class x ON x.oid=i.indexrelid
                LEFT JOIN pg_am am ON am.oid=x.relam
                WHERE n.nspname='tianji_agent' AND t.relname='agent_model_usage' AND c.contype<>'c'
                  AND c.contype<>'n'
                """, rs -> {
            String name = rs.getString("conname");
            if (!seen.add(name) || !rs.getBoolean("convalidated") || rs.getBoolean("condeferrable")
                    || rs.getBoolean("condeferred") || !MODEL_USAGE_KEY.equals(array(rs.getArray("columns")))) throw invalid();
            switch (name) {
                case "agent_model_usage_pkey" -> {
                    if (!"p".equals(rs.getString("contype")) || !name.equals(rs.getString("index_name"))
                            || !rs.getBoolean("indisprimary") || !rs.getBoolean("indisunique")
                            || !rs.getBoolean("indisvalid") || !rs.getBoolean("indisready") || !rs.getBoolean("indislive")
                            || !rs.getBoolean("indimmediate") || !"btree".equals(rs.getString("amname"))
                            || rs.getInt("indnkeyatts") != 3 || rs.getInt("indnatts") != 3
                            || !rs.getBoolean("plain_index") || !rs.getBoolean("ascending")
                            || !rs.getBoolean("default_classes") || !rs.getBoolean("column_collations")
                            || rs.getInt("index_count") != 1) throw invalid();
                }
                case "agent_model_usage_budget_fkey" -> {
                    if (!"f".equals(rs.getString("contype")) || !"tianji_agent".equals(rs.getString("ref_schema"))
                            || !"agent_budget_reservation".equals(rs.getString("ref_table"))
                            || !MODEL_USAGE_KEY.equals(array(rs.getArray("ref_columns")))
                            || !"a".equals(rs.getString("confupdtype")) || !"a".equals(rs.getString("confdeltype"))
                            || !"s".equals(rs.getString("confmatchtype"))) throw invalid();
                }
                default -> throw invalid();
            }
        });
        if (!seen.equals(Set.of("agent_model_usage_pkey", "agent_model_usage_budget_fkey"))) throw invalid();
    }

    private void verifyOptionalChecks(String table, String migration, int count, UnaryOperator<String> signature) {
        var expected = optionalChecks(table, migration, count, signature);
        var actual = new HashMap<String, String>();
        jdbc.query("""
                SELECT c.conname,c.convalidated,c.connoinherit,pg_get_constraintdef(c.oid) AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname=? AND c.contype='c'
                """, rs -> {
            if (!rs.getBoolean("convalidated") || rs.getBoolean("connoinherit")
                    || actual.put(rs.getString("conname"), signature.apply(rs.getString("definition"))) != null) {
                throw invalid();
            }
        }, table);
        if (!expected.equals(actual)) throw invalid();
    }

    private static Map<String, String> optionalChecks(String table, String migration, int count,
                                                      UnaryOperator<String> signature) {
        var checks = new HashMap<String, String>();
        var clauses = Pattern.compile("CONSTRAINT (" + Pattern.quote(table) + "_[a-z_]+) CHECK\\s*\\(").matcher(migration);
        while (clauses.find()) {
            int open = clauses.end() - 1;
            int end = checkEnd(migration, open);
            if (checks.put(clauses.group(1), signature.apply("CHECK " + migration.substring(open, end + 1))) != null) {
                throw invalid();
            }
        }
        if (checks.size() != count) throw invalid();
        return checks;
    }

    private static int checkEnd(String sql, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int index = open; index < sql.length(); index++) {
            char value = sql.charAt(index);
            if (value == '\'') {
                if (quoted && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') index++;
                else quoted = !quoted;
            } else if (!quoted && value == '(') depth++;
            else if (!quoted && value == ')' && --depth == 0) return index;
        }
        throw invalid();
    }

    private static String modelUsageCheckSignature(String definition) {
        // The pinned V15 CASE branches never mix AND and OR in one condition.
        return normalizedCheck(definition)
                .replace(">=0::numeric", ">=0").replace(">='0'::numeric", ">=0")
                .replace("<=9223372036854775807::numeric", "<='9223372036854775807'::numeric")
                .replace("][]", "]");
    }

    private void verifyModelUsageRole() {
        Boolean permitted = jdbc.queryForObject("""
                SELECT c.relpersistence='p' AND NOT c.relispartition AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity
                  AND has_schema_privilege(n.oid,'USAGE') AND NOT has_schema_privilege(n.oid,'CREATE')
                  AND NOT EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname=current_user AND (r.rolsuper OR r.rolbypassrls))
                  AND NOT pg_has_role(c.relowner,'MEMBER') AND NOT pg_has_role(n.nspowner,'MEMBER')
                  AND has_table_privilege(c.oid,'SELECT') AND has_table_privilege(c.oid,'INSERT')
                  AND NOT has_table_privilege(c.oid,'UPDATE,DELETE,TRUNCATE,TRIGGER,REFERENCES')
                  AND NOT has_any_column_privilege(c.oid,'REFERENCES')
                  AND NOT EXISTS (SELECT 1 FROM pg_trigger tr WHERE tr.tgrelid=c.oid
                    AND ((NOT tr.tgisinternal AND tr.tgenabled<>'D') OR (tr.tgisinternal AND tr.tgenabled<>'O')))
                  AND NOT EXISTS (SELECT 1 FROM pg_rewrite rule WHERE rule.ev_class=c.oid)
                  AND ARRAY(SELECT a.attname::text FROM pg_attribute a
                    WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
                      AND has_column_privilege(c.oid,a.attnum,'UPDATE') ORDER BY a.attname::text COLLATE "C")
                    = ARRAY['actual_cost','actual_input_tokens','actual_output_tokens','final_sha256','measured_at','safe_reason','state']
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname='tianji_agent' AND c.relname='agent_model_usage' AND c.relkind='r'
                """, Boolean.class);
        if (!Boolean.TRUE.equals(permitted)) throw invalid();
    }

    private static String pinnedMigration(String file, String sha256) {
        try (InputStream input = AgentStorageVerifier.class.getClassLoader()
                .getResourceAsStream("db/tianji-agent/" + file)) {
            if (input == null) throw invalid();
            byte[] bytes = input.readNBytes(8193);
            if (bytes.length > 8192 || !sha256.equals(HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) throw invalid();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw invalid();
        }
    }

    private static Map.Entry<String, UsageColumn> usageColumn(String name, String type, boolean required, boolean cCollation) {
        return Map.entry(name, new UsageColumn(type, required, cCollation, null));
    }

    private record UsageColumn(String type, boolean required, boolean cCollation, String defaultValue) { }

    private void verifyContextColumns() {
        Map<String, Column> expected = Map.ofEntries(column("tenant_id", "bigint", true, false), column("run_id", "character varying(64)", true, false),
                column("reservation_id", "character varying(128)", true, false), column("model_call_number", "integer", true, false),
                column("attempt", "integer", true, false), column("lease_epoch", "bigint", true, false), column("metadata", "jsonb", true, false),
                column("metadata_sha256", "character(64)", true, true), column("captured_at", "timestamp with time zone", true, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull,co.collname FROM pg_class t
                JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname='tianji_agent' AND t.relname='agent_context_snapshot' AND t.relkind IN ('r','p')
                """, rs -> {
            actual.put(rs.getString("attname"), new Column(rs.getString("type"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname"))));
        });
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    private static Map<String, String> contextChecks() {
        String fields = Arrays.stream(AgentContextMetadataDTO.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName)
                .sorted().map(value -> "'" + value + "'").collect(java.util.stream.Collectors.joining(","));
        return Map.ofEntries(
                Map.entry("agent_context_tenant_positive", "CHECKtenant_id>0"), Map.entry("agent_context_call_positive", "CHECKmodel_call_number>0"),
                Map.entry("agent_context_attempt_positive", "CHECKattempt>0"), Map.entry("agent_context_epoch_positive", "CHECKlease_epoch>0"),
                Map.entry("agent_context_metadata_hash", "CHECKmetadata_sha256~'^[0-9a-f]{64}$'"),
                Map.entry("agent_context_metadata_size", "CHECKoctet_lengthmetadata<=294912"), Map.entry("agent_context_metadata_object", "CHECKjsonb_typeofmetadata='object'"),
                Map.entry("agent_context_metadata_version", "CHECKmetadata->>'schemaVersion'='1'"),
                Map.entry("agent_context_projection_version", "CHECKmetadata->>'projectionVersion'='ADK_GENAI_MODEL_V1'"),
                Map.entry("agent_context_metadata_keys", "CHECKmetadata?&ARRAY[" + fields + "]"),
                Map.entry("agent_context_metadata_closed", "CHECKmetadata-ARRAY[" + fields + "]='{}'::jsonb"),
                Map.entry("agent_context_run_binding", "CHECKmetadata->'runId'=to_jsonbrun_id"),
                Map.entry("agent_context_call_binding", "CHECKmetadata->'reservationId'=to_jsonbreservation_id"),
                Map.entry("agent_context_epoch_binding", "CHECKmetadata->'leaseEpoch'=to_jsonblease_epoch"),
                Map.entry("agent_context_window_object", "CHECKjsonb_typeofmetadata->'window'='object'"),
                Map.entry("agent_context_sources_array", "CHECKjsonb_typeofmetadata->'sources'='array'"),
                Map.entry("agent_context_sources_size", "CHECKjsonb_array_lengthmetadata->'sources'>=1ANDjsonb_array_lengthmetadata->'sources'<=128"),
                Map.entry("agent_context_trims_array", "CHECKjsonb_typeofmetadata->'trims'='array'"),
                Map.entry("agent_context_trims_size", "CHECKjsonb_array_lengthmetadata->'trims'<=128"));
    }

    private void verifyServiceGrantColumns() {
        Map<String, Column> expected = Map.ofEntries(
                column("tenant_id", "bigint", true, false), column("run_id", "character varying(64)", true, false),
                column("subject_client_id", "character varying(128)", true, true), column("subject_app_id", "character varying(128)", true, true),
                column("subject_business_code", "character varying(128)", true, true),
                column("subject_application_code", "character varying(128)", true, true),
                column("subject_environment", "character varying(128)", true, true),
                column("subject_resource_uri", "character varying(2048)", true, true),
                column("binding_sha256", "character(64)", true, true), column("issue_epoch", "bigint", true, false),
                column("grant_id", "character varying(63)", false, true), column("ciphertext", "text", false, false),
                column("expires_at", "timestamp with time zone", false, false), column("updated_at", "timestamp with time zone", true, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname='tianji_agent' AND t.relname='agent_run_service_execution_grant' AND t.relkind IN ('r', 'p')
                """, rs -> {
            actual.put(rs.getString("attname"), new Column(rs.getString("type"), rs.getBoolean("attnotnull"),
                    "C".equals(rs.getString("collname"))));
        });
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    static String normalizedCheck(String definition) {
        if (definition == null) throw invalid();
        var result = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < definition.length(); i++) {
            char value = definition.charAt(i);
            if (value == '\'') {
                result.append(value);
                if (quoted && i + 1 < definition.length() && definition.charAt(i + 1) == '\'') result.append(definition.charAt(++i));
                else quoted = !quoted;
            } else if (quoted) result.append(value);
            else if (definition.startsWith("::character varying", i)) i += "::character varying".length() - 1;
            else if (definition.startsWith("::text", i)) i += "::text".length() - 1;
            else if (!Character.isWhitespace(value) && value != '(' && value != ')') result.append(value);
        }
        if (quoted) throw invalid();
        return result.toString();
    }

    private void verifyLinkedColumns() {
        Map<String, Column> expected = Map.ofEntries(
                column("tenant_id", "bigint", true, false),
                column("subject_issuer", "character varying(64)", true, true),
                column("subject_id", "character varying(256)", true, true),
                column("idempotency_key", "character varying(128)", true, true),
                column("source_id", "character varying(128)", true, true),
                column("scope_type", "character varying(8)", true, false),
                column("scope_id", "character varying(255)", true, true),
                column("occurrence_id", "character varying(255)", true, true),
                column("definition_sha256", "character(64)", true, true),
                column("dispatch_not_after", "timestamp with time zone", true, false),
                column("link_sha256", "character(64)", true, true),
                column("request_sha256", "character(64)", false, true),
                column("state", "character varying(8)", true, false),
                column("run_id", "character varying(64)", false, false),
                column("created_at", "timestamp with time zone", true, false),
                column("closed_at", "timestamp with time zone", false, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname='tianji_agent' AND t.relname='agent_process_dispatch' AND t.relkind IN ('r', 'p')
                """, rs -> {
            actual.put(rs.getString("attname"), new Column(rs.getString("type"), rs.getBoolean("attnotnull"),
                    "C".equals(rs.getString("collname"))));
        });
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    private void verifyLinkedChecks() {
        Set<String> checks = new HashSet<>();
        jdbc.query("""
                SELECT c.convalidated, pg_get_constraintdef(c.oid) AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname='agent_process_dispatch' AND c.contype='c'
                """, rs -> {
            if (!rs.getBoolean("convalidated")) throw invalid();
            checks.add(rs.getString("definition"));
        });
        if (!checks.containsAll(Set.of(
                "CHECK ((tenant_id > 0))",
                "CHECK (((subject_issuer)::text ~ '^[0-9a-f]{64}$'::text))",
                "CHECK ((definition_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "CHECK ((link_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "CHECK ((request_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "CHECK (((scope_type)::text = ANY ((ARRAY['BPMN'::character varying, 'CMMN'::character varying])::text[])))",
                "CHECK (((state)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying])::text[])))",
                "CHECK (((((state)::text = 'OPEN'::text) AND (closed_at IS NULL)) OR (((state)::text = 'CLOSED'::text) AND (closed_at IS NOT NULL))))",
                "CHECK ((((run_id IS NULL) AND (request_sha256 IS NULL)) OR ((run_id IS NOT NULL) AND (request_sha256 IS NOT NULL))))"))) throw invalid();
    }

    private void verifyHistory() {
        Map<String, Integer> expected = new HashMap<>();
        MIGRATIONS.forEach((version, file) -> expected.put(version, checksum(file)));
        Map<String, Integer> actual = new HashMap<>();
        jdbc.query("SELECT version, type, checksum, success FROM tianji_agent.flyway_schema_history", rs -> {
            if (!rs.getBoolean("success")) throw invalid();
            String version = rs.getString("version");
            if (version != null && MIGRATIONS.containsKey(version)) {
                if (!"SQL".equals(rs.getString("type")) || rs.getObject("checksum") == null
                        || actual.put(version, rs.getInt("checksum")) != null) throw invalid();
            }
        });
        if (!expected.equals(actual)) throw invalid();
    }

    private void verifyColumns() {
        COLUMNS.forEach((table, columns) -> jdbc.query(
                "SELECT " + columns + " FROM tianji_agent." + table + " WHERE false", (rs, row) -> 1));
    }

    private void verifyIdentityColumns() {
        Set<String> expected = new HashSet<>();
        for (String table : List.of("agent_run", "agent_run_command_receipt", "agent_session", "agent_session_event",
                "agent_session_scope_state", "agent_session_lease")) {
            expected.add(table + ".subject_issuer");
            expected.add(table + ".subject_id");
        }
        Set<String> actual = new HashSet<>();
        jdbc.query("""
                SELECT t.relname, a.attname, format_type(a.atttypid, a.atttypmod) AS type,
                       a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid = a.attcollation
                WHERE n.nspname = 'tianji_agent' AND t.relkind IN ('r', 'p')
                  AND a.attname IN ('subject_issuer', 'subject_id')
                """, rs -> {
            String key = rs.getString("relname") + "." + rs.getString("attname");
            if (expected.contains(key)) {
                String type = key.endsWith(".subject_issuer") ? "character varying(64)" : "character varying(256)";
                if (!type.equals(rs.getString("type")) || !rs.getBoolean("attnotnull")
                        || !"C".equals(rs.getString("collname"))) throw invalid();
                actual.add(key);
            }
        });
        if (!actual.equals(expected)) throw invalid();
    }

    private void verifyConstraints() {
        if (!readConstraints().containsAll(requiredConstraints())) throw invalid();
    }

    private Set<Key> readConstraints() {
        Set<Key> actual = new HashSet<>();
        jdbc.query("""
                SELECT t.relname, c.contype, c.convalidated, c.condeferrable, i.indisvalid, i.indisready,
                       rn.nspname AS ref_schema, rt.relname AS ref_table, c.confdeltype,
                       ARRAY(SELECT a.attname::text FROM unnest(c.conkey) WITH ORDINALITY k(num, pos)
                             JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num ORDER BY k.pos) AS columns,
                       ARRAY(SELECT a.attname::text FROM unnest(c.confkey) WITH ORDINALITY k(num, pos)
                             JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num ORDER BY k.pos) AS ref_columns
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_index i ON i.indexrelid=c.conindid
                LEFT JOIN pg_class rt ON rt.oid=c.confrelid LEFT JOIN pg_namespace rn ON rn.oid=rt.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relkind IN ('r', 'p') AND c.contype IN ('p', 'u', 'f')
                """, rs -> {
            String type = rs.getString("contype");
            if (!rs.getBoolean("convalidated") || rs.getBoolean("condeferrable")) return;
            if (!"f".equals(type) && (!rs.getBoolean("indisvalid") || !rs.getBoolean("indisready"))) return;
            if ("f".equals(type) && !"tianji_agent".equals(rs.getString("ref_schema"))) return;
            actual.add(new Key(rs.getString("relname"), type, array(rs.getArray("columns")),
                    rs.getString("ref_table"), array(rs.getArray("ref_columns")),
                    "f".equals(type) ? rs.getString("confdeltype") : null));
        });
        return actual;
    }

    private static Set<Key> requiredConstraints() {
        Set<Key> keys = new HashSet<>();
        keys.add(key("agent_definition", "p", "tenant_id", "definition_key"));
        keys.add(key("agent_definition_version", "p", "tenant_id", "definition_key", "version"));
        keys.add(key("agent_run", "p", "tenant_id", "run_id"));
        keys.add(key("agent_run", "u", "tenant_id", "subject_type", "subject_issuer", "subject_id", "idempotency_key"));
        keys.add(key("agent_event", "p", "tenant_id", "run_id", "sequence"));
        keys.add(key("agent_event", "u", "tenant_id", "event_id"));
        keys.add(key("agent_run_command_receipt", "p", "tenant_id", "subject_type", "subject_issuer", "subject_id", "operation", "command_id"));
        keys.add(key("agent_budget_reservation", "p", "tenant_id", "run_id", "reservation_id"));
        keys.add(key("agent_session", "p", SESSION_OWNER));
        keys.add(key("agent_session_event", "p", append(SESSION_OWNER, "sequence")));
        keys.add(key("agent_session_event", "u", append(SESSION_OWNER, "native_event_id")));
        keys.add(key("agent_session_scope_state", "p", "tenant_id", "app_name", "scope_type", "subject_type", "subject_issuer", "subject_id", "state_key"));
        keys.add(key("agent_session_lease", "p", SESSION_OWNER));
        keys.add(key("agent_run_execution_grant", "p", RUN_KEY));
        keys.add(foreign("agent_definition_version", List.of("tenant_id", "definition_key"), "agent_definition", List.of("tenant_id", "definition_key"), "a"));
        keys.add(foreign("agent_run", List.of("tenant_id", "definition_key", "definition_version"), "agent_definition_version", List.of("tenant_id", "definition_key", "version"), "a"));
        for (String table : List.of("agent_event", "agent_run_command_receipt", "agent_budget_reservation")) {
            keys.add(foreign(table, RUN_KEY, "agent_run", RUN_KEY, "a"));
        }
        keys.add(foreign("agent_run_execution_grant", RUN_KEY, "agent_run", RUN_KEY, "c"));
        keys.add(foreign("agent_session_event", SESSION_OWNER, "agent_session", SESSION_OWNER, "c"));
        keys.add(foreign("agent_session_lease", SESSION_OWNER, "agent_session", SESSION_OWNER, "c"));
        return keys;
    }

    private void verifyNativeEventIndex() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class x ON x.oid=i.indexrelid
                WHERE n.nspname='tianji_agent' AND t.relname='agent_event' AND x.relname='agent_event_native_id_idx'
                  AND i.indisunique AND i.indisvalid AND i.indisready AND i.indimmediate
                  AND i.indnkeyatts=3 AND i.indnatts=3
                  AND ARRAY(SELECT a.attname::text FROM unnest(i.indkey) WITH ORDINALITY k(num, pos)
                            JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=k.num ORDER BY k.pos)
                      = ARRAY['tenant_id', 'run_id', 'native_event_id']
                  AND pg_get_expr(i.indpred, i.indrelid)='(native_event_id IS NOT NULL)'
                """, Integer.class);
        if (count == null || count != 1) throw invalid();
    }

    static int checksum(String file) {
        try (InputStream stream = AgentStorageVerifier.class.getClassLoader().getResourceAsStream("db/tianji-agent/" + file)) {
            if (stream == null) throw invalid();
            CRC32 crc = new CRC32();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (first && line.startsWith("\ufeff")) line = line.substring(1);
                    first = false;
                    crc.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
            return (int) crc.getValue();
        } catch (IOException failure) {
            throw invalid();
        }
    }

    private static List<String> array(java.sql.Array value) throws java.sql.SQLException {
        return value == null ? List.of() : List.copyOf(Arrays.asList((String[]) value.getArray()));
    }
    private static List<String> append(List<String> values, String last) {
        var result = new java.util.ArrayList<>(values);
        result.add(last);
        return List.copyOf(result);
    }
    private static Key key(String table, String type, String... columns) { return key(table, type, List.of(columns)); }
    private static Key key(String table, String type, List<String> columns) { return new Key(table, type, columns, null, List.of(), null); }
    private static Key foreign(String table, List<String> columns, String target, List<String> targetColumns, String deletion) {
        return new Key(table, "f", columns, target, targetColumns, deletion);
    }
    private static Map.Entry<String, Column> column(String name, String type, boolean required, boolean cCollation) {
        return Map.entry(name, new Column(type, required, cCollation));
    }
    private static AgentLedgerException invalid() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent storage verification failed");
    }
    private record Key(String table, String type, List<String> columns, String target, List<String> targetColumns, String deletion) { }
    private record Column(String type, boolean required, boolean cCollation) { }
}
