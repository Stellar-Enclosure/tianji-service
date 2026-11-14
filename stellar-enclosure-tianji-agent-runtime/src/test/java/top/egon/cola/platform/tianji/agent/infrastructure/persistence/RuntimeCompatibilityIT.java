package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCompatibilityIT {
    static final String MIGRATION = "V17__add_runtime_safety_receipts.sql";
    static final String HASH = "a".repeat(64);

    @Test
    void upgradesRealV16WithoutRewritingLegacyDefinitionsRunsOrJournal() throws Exception {
        var db = provisionV16("compatibility");
        var owner = owner();
        String run = UUID.randomUUID().toString();
        insertRun(db.admin(), owner, run, "{}");
        UUID intent = UUID.randomUUID();
        db.admin().update("""
                INSERT INTO tianji_agent.agent_tool_action_intent
                  (tenant_id,intent_id,logical_identity_sha256,owner_type,owner_issuer,owner_id,action_key,business_key,
                   business_version,tool_key,tool_version,tool_sha256,binding_sha256,request_sha256,descriptor,arguments,
                   origin_run_id,origin_snapshot_sha256,risk,state,revision,side_effect_outcome,created_at,updated_at,expires_at)
                VALUES (?,?,?,'USER',?,?,'action','business','v1','tool',1,?,?,?,'{}','{}',?,?,
                   'IDEMPOTENT_WRITE','PREPARED',1,'NONE',statement_timestamp(),statement_timestamp(),
                   statement_timestamp()+interval '1 hour')
                """, owner.tenantId(), intent, HASH, owner.issuerFingerprint(), owner.subjectId(), HASH, HASH, HASH, run, HASH);

        var history = historicalChecksums(db.admin());
        var golden = legacyRows(db.admin(), run, intent);
        var originalFks = actionFks(db.admin());
        var legacyColumns = actionColumns(db.admin());
        assertEquals(43, legacyColumns.size());
        assertEquals(16, history.size());
        assertTrue(originalFks.stream().anyMatch(value -> value.contains("dispatch_invocation_id") && value.contains("agent_tool_invocation")));
        assertNull(db.admin().queryForObject("SELECT to_regclass('tianji_agent.agent_runtime_binding')::text", String.class));

        db.installV17();
        assertPackagedManifest();
        assertEquals(history, historicalChecksums(db.admin()));
        assertNotEquals(legacyColumns, actionColumns(db.admin()), "The legacy verifier's exact column equality rejects V17");
        assertEquals(List.of("action_profile", "dispatch_call_id"), actionColumns(db.admin()).stream()
                .filter(column -> !legacyColumns.contains(column)).toList());
        assertEquals(golden, legacyRows(db.admin(), run, intent));
        assertTrue(actionFks(db.admin()).containsAll(originalFks));
        assertEquals("LEGACY_V1", db.admin().queryForObject("""
                SELECT action_profile FROM tianji_agent.agent_tool_action_intent WHERE intent_id=?
                """, String.class, intent));
        assertEquals(0, db.admin().queryForObject("SELECT count(*) FROM tianji_agent.agent_runtime_binding", Integer.class));
        var runtime = db.runtime();
        var verifier = new AgentStorageVerifier(runtime);
        verifier.verifyRuntimeStorage();
        new ToolExecutionStorageVerifier(runtime).verifyResultAccess();
        assertTrue(Boolean.TRUE.equals(new JdbcTemplate(runtime).queryForObject("""
                SELECT has_table_privilege('tianji_agent.agent_tool_result_access','SELECT')
                    AND has_table_privilege('tianji_agent.agent_tool_result_access','INSERT')
                    AND NOT has_table_privilege('tianji_agent.agent_tool_result_access','UPDATE')
                """, Boolean.class)));

        var transaction = new TransactionTemplate(new DataSourceTransactionManager(db.source()));
        transaction.executeWithoutResult(status -> {
            db.admin().execute("SET LOCAL enable_seqscan=off");
            var plan = db.admin().queryForList("""
                    EXPLAIN (COSTS OFF) SELECT call_id FROM tianji_agent.agent_runtime_call_receipt
                    WHERE tenant_id=11 AND run_id=? AND state='PREPARED' AND deadline_at<now()
                    ORDER BY deadline_at LIMIT 100
                    """, String.class, run);
            assertTrue(plan.stream().anyMatch(row -> row.contains("runtime_call_pending_idx")), plan.toString());
        });

        db.admin().update("UPDATE tianji_agent.flyway_schema_history SET checksum=checksum+1 WHERE version='17'");
        assertUnavailable(verifier);
        db.admin().update("UPDATE tianji_agent.flyway_schema_history SET checksum=checksum-1 WHERE version='17'");
        verifier.verifyRuntimeStorage();

        db.admin().execute("ALTER TABLE tianji_agent.agent_runtime_call_receipt DROP CONSTRAINT runtime_call_capability_fkey");
        assertUnavailable(verifier);
        db.admin().execute("""
                ALTER TABLE tianji_agent.agent_runtime_call_receipt ADD CONSTRAINT runtime_call_capability_fkey
                FOREIGN KEY (tenant_id,run_id,capability_id,capability_binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256)
                REFERENCES tianji_agent.agent_tool_capability
                  (tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256) ON DELETE NO ACTION
                """);
        verifier.verifyRuntimeStorage();
        db.admin().execute("DROP INDEX tianji_agent.runtime_call_pending_idx");
        assertUnavailable(verifier);
    }

    private static void assertPackagedManifest() throws Exception {
        try (var manifest = RuntimeCompatibilityIT.class.getResourceAsStream("/db/tianji-agent/manifest.json");
             var sql = RuntimeCompatibilityIT.class.getResourceAsStream("/db/tianji-agent/" + MIGRATION)) {
            assertNotNull(manifest);
            assertNotNull(sql);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.readAllBytes()));
            var mapper = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            assertEquals(mapper.valueToTree(Map.of("schemaVersion", 1, "scripts",
                    List.of(Map.of("version", 17, "file", MIGRATION, "sha256", digest)))), mapper.readTree(manifest));
        }
    }

    private static List<String> actionColumns(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT attname FROM pg_attribute
                WHERE attrelid='tianji_agent.agent_tool_action_intent'::regclass AND attnum>0 AND NOT attisdropped
                ORDER BY attnum
                """, String.class);
    }

    private static void assertUnavailable(AgentStorageVerifier verifier) {
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                assertThrows(AgentLedgerException.class, verifier::verifyRuntimeStorage).code());
    }

    private static List<Map<String, Object>> historicalChecksums(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT installed_rank,version,script,checksum,success FROM tianji_agent.flyway_schema_history
                WHERE version IS NOT NULL AND version<>'17' ORDER BY installed_rank
                """);
    }

    private static Map<String, Object> legacyRows(JdbcTemplate jdbc, String run, UUID intent) {
        return jdbc.queryForMap("""
                SELECT to_jsonb(d)::text AS definition, to_jsonb(r)::text AS run,
                       (to_jsonb(a)-'action_profile'-'dispatch_call_id')::text AS action
                FROM tianji_agent.agent_run r
                JOIN tianji_agent.agent_definition_version d USING (tenant_id,definition_key)
                JOIN tianji_agent.agent_tool_action_intent a ON a.tenant_id=r.tenant_id AND a.origin_run_id=r.run_id
                WHERE r.run_id=? AND a.intent_id=?
                """, run, intent);
    }

    private static List<String> actionFks(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conrelid='tianji_agent.agent_tool_action_intent'::regclass AND contype='f'
                ORDER BY conname
                """, String.class);
    }

    static TrustedAgentActor owner() {
        return TrustedAgentActor.verified(11, TrustedAgentActor.SubjectType.USER,
                "owner-" + UUID.randomUUID(), "https://runtime-fixture.example/issuer");
    }

    static void insertRun(JdbcTemplate jdbc, TrustedAgentActor owner, String runId, String snapshotJson) {
        String definitionKey = "def-" + runId;
        jdbc.update("""
                INSERT INTO tianji_agent.agent_definition (tenant_id,definition_key,active_version,created_at)
                VALUES (?,?,1,statement_timestamp())
                """, owner.tenantId(), definitionKey);
        jdbc.update("""
                INSERT INTO tianji_agent.agent_definition_version
                    (tenant_id,definition_key,version,content_sha256,specification,created_at)
                VALUES (?,?,1,?,'{}',statement_timestamp())
                """, owner.tenantId(), definitionKey, HASH);
        jdbc.update("""
                INSERT INTO tianji_agent.agent_run
                   (tenant_id,run_id,definition_key,definition_version,subject_type,subject_issuer,subject_id,
                    idempotency_key,request_hash,status,revision,attempt,lease_epoch,lease_owner,lease_expires_at,
                    deadline_at,execution_snapshot,created_at)
                VALUES (?,?,?,1,'USER',?,?,?,?,'RUNNING',1,1,1,'worker',statement_timestamp()+interval '5 minutes',
                        statement_timestamp()+interval '5 minutes',?::jsonb,statement_timestamp())
                """, owner.tenantId(), runId, definitionKey, owner.issuerFingerprint(), owner.subjectId(), runId, HASH, snapshotJson);
    }

    static Fixture provisionV16(String purpose) {
        String url = url(purpose);
        String vector = System.getProperty("tianji.runtime.safety.postgres.vector.version");
        assertTrue(vector != null && vector.matches("[0-9]+\\.[0-9]+\\.[0-9]+"), "Explicit pgvector patch required");
        var source = new DriverManagerDataSource(url);
        var connection = new Properties();
        connection.setProperty("connectTimeout", "5");
        connection.setProperty("socketTimeout", "15");
        source.setConnectionProperties(connection);
        var admin = new JdbcTemplate(source);
        admin.setQueryTimeout(10);
        assertEquals(0, admin.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        assertEquals(vector, admin.queryForObject("""
                SELECT e.extversion FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
                WHERE e.extname='vector' AND n.nspname='public'
                """, String.class));
        assertEquals(16, flyway(source, "16").migrate().migrationsExecuted);
        return new Fixture(source, admin, url);
    }

    private static String url(String purpose) {
        String value = System.getProperty("tianji.runtime.safety.postgres." + purpose + ".url");
        assertTrue(value != null && value.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}"),
                "Runtime safety tests require an explicitly provisioned empty loopback tianji_it_ database");
        assertNotEquals(System.getProperty("tianji.tool.journal.postgres.test.url"), value);
        assertNotEquals(System.getProperty("tianji.runtime.safety.postgres." + ("receipt".equals(purpose) ? "compatibility" : "receipt") + ".url"), value);
        return value;
    }

    private static Flyway flyway(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                .target(target).cleanDisabled(true).load();
    }

    record Fixture(DriverManagerDataSource source, JdbcTemplate admin, String url) {
        void installV17() {
            assertNotNull(RuntimeCompatibilityIT.class.getResource("/db/tianji-agent/" + MIGRATION),
                    "Runtime safety migration V17 is required after the real V16 baseline");
            assertEquals(1, flyway(source, "17").migrate().migrationsExecuted);
        }

        PGSimpleDataSource runtime() {
            String role = System.getenv().getOrDefault("TIANJI_AR_RUNTIME_TEST_USER", "tianji_it_agent_runtime");
            assertTrue(role.matches("[a-z][a-z0-9_]{0,62}"), "Explicit restricted runtime fixture role is required");
            var source = new PGSimpleDataSource();
            source.setURL(url);
            source.setUser(role);
            source.setConnectTimeout(5);
            source.setSocketTimeout(15);
            var jdbc = new JdbcTemplate(source);
            assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT current_user=? AND NOT r.rolsuper AND NOT r.rolbypassrls AND NOT r.rolcreaterole
                        AND NOT r.rolcreatedb AND NOT r.rolreplication
                        AND NOT pg_has_role(d.datdba,'MEMBER') AND NOT has_database_privilege(d.oid,'CREATE')
                        AND NOT has_schema_privilege('public','CREATE')
                    FROM pg_roles r CROSS JOIN pg_database d
                    WHERE r.rolname=current_user AND d.datname=current_database()
                    """, Boolean.class, role)));
            String grantee = "\"" + role + "\"";
            admin.execute("GRANT USAGE ON SCHEMA tianji_agent TO " + grantee);
            admin.execute("GRANT SELECT ON tianji_agent.flyway_schema_history, tianji_agent.agent_run TO " + grantee);
            admin.execute("GRANT UPDATE (revision) ON tianji_agent.agent_run TO " + grantee);
            admin.execute("""
                    GRANT SELECT, INSERT, UPDATE ON tianji_agent.agent_runtime_binding,
                      tianji_agent.agent_runtime_call_receipt, tianji_agent.agent_tool_capability,
                      tianji_agent.agent_tool_action_intent, tianji_agent.agent_tool_invocation TO
                    """ + grantee);
            admin.execute("GRANT SELECT, INSERT ON tianji_agent.agent_tool_result_access TO " + grantee);
            return source;
        }
    }
}
