package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "tianji.memory.receipt-epoch.postgres.test.url", matches = ".+")
@Timeout(90)
class LegacyMemoryReceiptEpochPostgresTest {
    private static final String RECEIPTS = "tianji_agent.agent_memory_command_receipt";
    private static final String HISTORY = "tianji_agent.flyway_schema_history";
    private static final String HASH = "a".repeat(64);
    private static final String MODEL = "memory-test@1#" + HASH;
    private static final String COMMAND = "original-command";
    private static final String OLD_ACTOR = "legacy";

    @Test
    void upgradesRealV17AndEnforcesTheGuardedReceiptColumn() {
        String url = System.getProperty("tianji.memory.receipt-epoch.postgres.test.url");
        String vectorVersion = System.getProperty("tianji.memory.postgres.vector.version");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}"));
        assertNotEquals(System.getProperty("tianji.runtime.safety.postgres.compatibility.url"), url);
        assertTrue(vectorVersion != null && vectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+"));
        var source = new DriverManagerDataSource(url);
        var admin = new JdbcTemplate(source);
        admin.setQueryTimeout(15);
        assertEquals(0, admin.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        assertEquals(vectorVersion, admin.queryForObject("""
                SELECT e.extversion FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
                WHERE e.extname='vector' AND n.nspname='public'
                """, String.class));

        assertEquals(17, migrationTo(source, "17").migrate().migrationsExecuted);
        seedLegacyReceipt(source, admin);
        var previousHistory = admin.queryForList("SELECT * FROM " + HISTORY + " WHERE version IS NOT NULL ORDER BY installed_rank");
        String originalReceipt = admin.queryForObject("SELECT to_jsonb(r)::text FROM " + RECEIPTS + " r", String.class);
        var runtime = runtimeSource(url, admin);
        var verifier = new MemoryStorageVerifier(runtime, 2, vectorVersion, new MemoryIndexPublication.Binding(MODEL, 2, "agent_memory_vector_v1"));
        var runtimeVerifier = new AgentStorageVerifier(runtime);
        verifier.verify();
        runtimeVerifier.verifyRuntimeStorage();
        assertUnavailable(verifier::verifyGuardedHost);
        admin.execute("ALTER TABLE " + RECEIPTS + " ADD COLUMN authority_epoch bigint");
        assertUnavailable(verifier::verify);
        assertUnavailable(verifier::verifyGuardedHost);
        admin.execute("ALTER TABLE " + RECEIPTS + " DROP COLUMN authority_epoch");

        var v18 = migrationTo(source, "18");
        assertEquals(1, v18.migrate().migrationsExecuted);
        v18.validate();
        assertEquals(previousHistory, admin.queryForList("SELECT * FROM " + HISTORY + " WHERE version IS NOT NULL AND version<>'18' ORDER BY installed_rank"));
        assertEquals(originalReceipt, admin.queryForObject("SELECT (to_jsonb(r)-'authority_epoch')::text FROM " + RECEIPTS + " r", String.class));
        assertNull(admin.queryForObject("SELECT authority_epoch FROM " + RECEIPTS, Long.class));
        verifier.verify();
        verifier.verifyGuardedHost();
        runtimeVerifier.verifyRuntimeStorage();

        var writer = new JdbcTemplate(runtime);
        assertEquals(1, insertGuardedReceipt(writer, "guarded-first", 1));
        assertEquals(1, insertGuardedReceipt(writer, "guarded-after-reopen", 3));
        assertThrows(DataIntegrityViolationException.class, () -> insertGuardedReceipt(writer, "invalid", 0));
        assertThrows(DuplicateKeyException.class, () -> insertGuardedReceipt(writer, OLD_ACTOR, 3));
        assertEquals(Map.of("guarded-first", 1L, "guarded-after-reopen", 3L), writer.query(
                "SELECT actor_id,authority_epoch FROM " + RECEIPTS + " WHERE actor_id<>? AND command_id=?",
                rs -> {
                    var epochs = new HashMap<String, Long>();
                    while (rs.next()) epochs.put(rs.getString(1), rs.getLong(2));
                    return epochs;
                }, OLD_ACTOR, COMMAND));

        admin.update("UPDATE " + HISTORY + " SET checksum=checksum+1 WHERE version='18'");
        assertUnavailable(verifier::verifyGuardedHost);
        assertUnavailable(runtimeVerifier::verifyRuntimeStorage);
        admin.update("UPDATE " + HISTORY + " SET checksum=checksum-1 WHERE version='18'");
        admin.update("UPDATE " + HISTORY + " SET version='180' WHERE version='18'");
        assertUnavailable(verifier::verify);
        assertUnavailable(verifier::verifyGuardedHost);
        assertUnavailable(runtimeVerifier::verifyRuntimeStorage);
        admin.update("UPDATE " + HISTORY + " SET version='18' WHERE version='180'");
        int installedRank = admin.queryForObject("SELECT installed_rank FROM " + HISTORY + " WHERE version='18'", Integer.class);
        admin.update("UPDATE " + HISTORY + " SET installed_rank=-1 WHERE version='18'");
        assertUnavailable(runtimeVerifier::verifyRuntimeStorage);
        admin.update("UPDATE " + HISTORY + " SET installed_rank=? WHERE version='18'", installedRank);

        admin.execute("ALTER TABLE " + RECEIPTS + " ALTER COLUMN authority_epoch SET DEFAULT 1");
        assertUnavailable(verifier::verifyGuardedHost);
        admin.execute("ALTER TABLE " + RECEIPTS + " ALTER COLUMN authority_epoch DROP DEFAULT");
        admin.execute("ALTER TABLE " + RECEIPTS + " DROP CONSTRAINT memory_command_authority_epoch");
        admin.execute("ALTER TABLE " + RECEIPTS + " ADD CONSTRAINT memory_command_authority_epoch CHECK (authority_epoch >= 0)");
        assertUnavailable(verifier::verifyGuardedHost);
        admin.execute("ALTER TABLE " + RECEIPTS + " DROP CONSTRAINT memory_command_authority_epoch");
        admin.execute("ALTER TABLE " + RECEIPTS + " ADD CONSTRAINT memory_command_authority_epoch CHECK (authority_epoch > 0)");
        verifier.verifyGuardedHost();
        runtimeVerifier.verifyRuntimeStorage();
    }

    private static Flyway migrationTo(DataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                .target(target).cleanDisabled(true).load();
    }

    private static PGSimpleDataSource runtimeSource(String url, JdbcTemplate admin) {
        String role = System.getenv().getOrDefault("TIANJI_AR_RUNTIME_TEST_USER", "tianji_it_agent_runtime");
        assertTrue(role.matches("[a-z][a-z0-9_]{0,62}"));
        var source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser(role);
        source.setConnectTimeout(5);
        source.setSocketTimeout(15);
        assertTrue(Boolean.TRUE.equals(new JdbcTemplate(source).queryForObject("""
                SELECT current_user=? AND NOT rol.rolsuper AND NOT rol.rolbypassrls AND NOT rol.rolcreaterole
                    AND NOT rol.rolcreatedb AND NOT rol.rolreplication AND NOT pg_has_role(db.datdba,'MEMBER')
                    AND NOT has_schema_privilege('public','CREATE') AND NOT has_database_privilege(db.oid,'CREATE')
                FROM pg_roles rol CROSS JOIN pg_database db
                WHERE rol.rolname=current_user AND db.datname=current_database()
                """, Boolean.class, role)));
        String grantee = '"' + role + '"';
        admin.execute("GRANT USAGE ON SCHEMA tianji_agent TO " + grantee);
        admin.execute("GRANT SELECT ON ALL TABLES IN SCHEMA tianji_agent TO " + grantee);
        admin.execute("""
                GRANT SELECT,INSERT,UPDATE ON tianji_agent.agent_context_snapshot,tianji_agent.agent_memory_scope,
                  tianji_agent.agent_memory_entry,tianji_agent.agent_memory_revision,tianji_agent.agent_memory_command_receipt,
                  tianji_agent.agent_memory_index_manifest,tianji_agent.agent_memory_run_grant,tianji_agent.agent_memory_vector_v1,
                  tianji_agent.agent_runtime_binding,tianji_agent.agent_runtime_call_receipt,tianji_agent.agent_tool_capability,
                  tianji_agent.agent_tool_action_intent,tianji_agent.agent_tool_invocation TO
                """ + grantee);
        admin.execute("GRANT DELETE ON tianji_agent.agent_memory_vector_v1 TO " + grantee);
        return source;
    }

    private static void seedLegacyReceipt(DataSource source, JdbcTemplate admin) {
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> {
            admin.update("""
                    INSERT INTO tianji_agent.agent_memory_scope
                        (tenant_id,owner_issuer,owner_type,owner_id,scope_id,created_at,updated_at)
                    VALUES (11,?,'USER','owner','scope',transaction_timestamp(),transaction_timestamp())
                    """, HASH);
            admin.update("""
                    INSERT INTO tianji_agent.agent_memory_entry
                        (tenant_id,owner_issuer,owner_type,owner_id,scope_id,memory_id,kind,current_revision,state,
                         content_sha256,confirmed,sensitivity,retention_policy_ref,index_model_ref,effective_at,expires_at,created_at,updated_at)
                    VALUES (11,?,'USER','owner','scope','memory','FACT',1,'CANDIDATE',?,false,'INTERNAL','policy',?,
                            transaction_timestamp(),transaction_timestamp()+interval '1 day',transaction_timestamp(),transaction_timestamp())
                    """, HASH, HASH, MODEL);
            admin.update("""
                    INSERT INTO tianji_agent.agent_memory_revision
                        (tenant_id,owner_issuer,owner_type,owner_id,scope_id,memory_id,revision,state,content_json,
                         source_json,content_sha256,source_evidence_sha256,source_actor_sha256,confidence,confirmed,sensitivity,
                         retention_policy_ref,index_model_ref,effective_at,expires_at,written_at,reason_code)
                    VALUES (11,?,'USER','owner','scope','memory',1,'CANDIDATE','{"facts":{},"text":"old"}',
                            jsonb_build_object('evidenceSha256',?::text,'reference','legacy','sourceEventId',NULL,'sourceRunId',NULL),
                            ?,?,?,1,false,'INTERNAL','policy',?,transaction_timestamp(),transaction_timestamp()+interval '1 day',
                            transaction_timestamp(),'CREATED')
                    """, HASH, HASH, HASH, HASH, HASH, MODEL);
            admin.update("""
                    INSERT INTO tianji_agent.agent_memory_command_receipt
                        (tenant_id,owner_issuer,owner_type,owner_id,scope_id,actor_type,actor_id,operation,command_id,
                         request_sha256,result_memory_id,result_revision,result_outcome,result_state,created_at)
                    VALUES (11,?,'USER','owner','scope','USER',?,'UPSERT',?,?,'memory',1,'CREATED','CANDIDATE',transaction_timestamp())
                    """, HASH, OLD_ACTOR, COMMAND, HASH);
        });
    }

    private static int insertGuardedReceipt(JdbcTemplate jdbc, String actor, long epoch) {
        return jdbc.update("""
                INSERT INTO tianji_agent.agent_memory_command_receipt
                    (tenant_id,owner_issuer,owner_type,owner_id,scope_id,actor_type,actor_id,operation,command_id,
                     request_sha256,result_memory_id,result_revision,result_outcome,result_state,created_at,authority_epoch)
                VALUES (11,?,'USER','owner','scope','USER',?,'UPSERT',?,?,'memory',1,'DEDUPLICATED','CANDIDATE',transaction_timestamp(),?)
                """, HASH, actor, COMMAND, HASH, epoch);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable read) {
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, read).code());
    }
}
