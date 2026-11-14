package top.egon.cola.platform.tianji.process.server;

import org.flywaydb.core.Flyway;
import org.flowable.cmmn.api.CmmnRepositoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated("Owns one explicitly provisioned, empty Process database")
@Timeout(120)
class AutomationProcessSchemaPostgresTest {
    private static final String SCHEMA = "tianji_process";
    private static final String HISTORY = SCHEMA + ".tianji_process_schema_history";
    private static final String V14 = "V14__add_automation_execution_bindings.sql";
    private static final String RELEASE = "tianji_process_automation_release";
    private static final String DRAFT = "tianji_process_draft";
    private static final String DRAFT_COMMAND = "tianji_process_draft_command";
    private static final String ACTIVATION = "tianji_process_automation_activation";
    private static final String BINDING = "tianji_process_instance_binding";
    private static final String NODE = "tianji_process_node_execution";

    @Test
    void adds_v14_without_rewriting_history_and_gates_the_runtime_catalog_and_role() throws Exception {
        String url = ProcessServerPostgresTest.explicitTestUrl();
        var ownerSource = source(url, null);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(10);
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT current_database() LIKE 'tianji_it_%' AND NOT pg_is_in_recovery()
                    AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'
                """, Boolean.class)));
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA));
        owner.execute("CREATE SCHEMA " + SCHEMA);
        var v13 = process(ownerSource, "13");
        assertEquals(13, v13.migrate().migrationsExecuted);
        v13.validate();
        assertEquals(IntStream.rangeClosed(1, 13).mapToObj(version -> version + ":SQL").toList(),
                owner.queryForList("SELECT concat(version,':',type) FROM " + HISTORY + " ORDER BY installed_rank", String.class));
        var original = owner.queryForList("SELECT * FROM " + HISTORY + " ORDER BY installed_rank");

        String scopedUrl = url + "?currentSchema=" + SCHEMA;
        try (var nativeSetup = ProcessServerPostgresTest.context(ProcessServerPostgresTest.Provisioner.class,
                scopedUrl, System.getProperty("user.name"), "--flowable.database-schema-update=true",
                "--egon.cola.component.transactional-outbox.enabled=false")) {
            assertNotNull(nativeSetup.getBean(RepositoryService.class));
            assertNotNull(nativeSetup.getBean(CmmnRepositoryService.class));
            assertNotNull(nativeSetup.getBean(DmnRepositoryService.class));
        }
        String role = ProcessServerPostgresTest.explicitRuntimeRole(owner);
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT NOT rolreplication AND NOT rolbypassrls AND NOT has_schema_privilege(rolname,'public','CREATE')
                FROM pg_roles WHERE rolname=?
                """, Boolean.class, role)));
        String quotedRole = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quotedRole);
        grant(owner, quotedRole, "SELECT", "tianji_process_schema_history");
        grant(owner, quotedRole, "SELECT, INSERT, UPDATE", "tianji_process_release", "tianji_process_release_definition",
                "tianji_process_command_receipt");
        grant(owner, quotedRole, "SELECT, INSERT", "tianji_governed_process_start");
        grant(owner, quotedRole, "SELECT", "tianji_platform_release_activation");

        var runtime = source(scopedUrl, role);
        var verifier = new ProcessApiSchemaVerifier(runtime, SCHEMA);
        verifier.verify();
        verifier.verifyGovernedProcessStart();
        assertTrue(verifier.hasCaseMigration());
        assertNull(owner.queryForObject("SELECT to_regclass(?)::text", String.class, SCHEMA + "." + RELEASE));
        try (var migration = getClass().getResourceAsStream("/db/tianji-process/" + V14)) {
            assertNotNull(migration, "V14 is required after installing and verifying the actual V13 baseline");
        }
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);

        var v14 = process(ownerSource, "14");
        assertEquals(1, v14.migrate().migrationsExecuted);
        v14.validate();
        assertEquals(original, owner.queryForList("SELECT * FROM " + HISTORY + " WHERE version<>'14' ORDER BY installed_rank"));
        assertManifest();
        verifier.verify();
        verifier.verifyGovernedProcessStart();
        assertTrue(verifier.hasCaseMigration());
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        grant(owner, quotedRole, "SELECT, INSERT", RELEASE, DRAFT_COMMAND, ACTIVATION);
        grant(owner, quotedRole, "SELECT, INSERT, UPDATE", DRAFT, NODE);
        grant(owner, quotedRole, "SELECT, INSERT", BINDING);
        owner.execute("GRANT UPDATE (created_at) ON " + SCHEMA + "." + BINDING + " TO " + quotedRole);
        verifier.verifyAutomationV14();

        owner.update("UPDATE " + HISTORY + " SET checksum=checksum+1 WHERE version='14'");
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        verifier.verify();
        owner.update("UPDATE " + HISTORY + " SET checksum=checksum-1 WHERE version='14'");
        verifier.verifyAutomationV14();

        var tx = new TransactionTemplate(new DataSourceTransactionManager(ownerSource));
        tx.executeWithoutResult(status -> {
            owner.execute("SET LOCAL enable_seqscan=off");
            var plan = owner.queryForList("""
                    EXPLAIN (COSTS OFF) SELECT draft_id FROM tianji_process.tianji_process_draft
                    WHERE tenant_id=1 AND owner_type='USER' AND owner_issuer=repeat('a',64)::char(64) COLLATE "C" AND owner_id='owner'
                        AND state='EDITING'
                    ORDER BY created_at DESC,draft_id DESC LIMIT 20
                    """, String.class);
            assertTrue(plan.stream().anyMatch(line -> line.contains("process_draft_owner_state_idx")), plan.toString());
            assertTrue(plan.stream().noneMatch(line -> line.contains("Sort")), plan.toString());
        });

        owner.update("UPDATE " + HISTORY + " SET installed_rank=20 WHERE version='7'");
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        owner.update("UPDATE " + HISTORY + " SET installed_rank=7 WHERE version='7'");
        grant(owner, quotedRole, "UPDATE", "tianji_process_schema_history");
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        owner.execute("REVOKE UPDATE ON " + HISTORY + " FROM " + quotedRole);
        verifier.verifyAutomationV14();

        var runtimeJdbc = new JdbcTemplate(runtime);
        assertStoredAutomationGroups(runtimeJdbc);
        assertEquals("instance", runtimeJdbc.queryForObject("""
                SELECT instance_id FROM tianji_process.tianji_process_instance_binding
                WHERE tenant_id=1 AND instance_id='instance' FOR UPDATE
                """, String.class));
        grant(owner, quotedRole, "UPDATE", BINDING);
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        owner.execute("REVOKE UPDATE ON " + SCHEMA + "." + BINDING + " FROM " + quotedRole);
        // A table-level REVOKE of UPDATE also revokes existing grants on its individual columns.
        owner.execute("GRANT UPDATE (created_at) ON " + SCHEMA + "." + BINDING + " TO " + quotedRole);
        verifier.verifyAutomationV14();
        grant(owner, quotedRole, "UPDATE", ACTIVATION);
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        owner.execute("REVOKE UPDATE ON " + SCHEMA + "." + ACTIVATION + " FROM " + quotedRole);
        verifier.verifyAutomationV14();

        owner.execute("DROP INDEX " + SCHEMA + ".process_draft_owner_state_idx");
        owner.execute("""
                CREATE INDEX process_draft_owner_state_idx ON tianji_process.tianji_process_draft
                  (tenant_id,owner_type,owner_issuer,owner_id,state,created_at,draft_id)
                """);
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        owner.execute("DROP INDEX " + SCHEMA + ".process_draft_owner_state_idx");
        owner.execute("""
                CREATE INDEX process_draft_owner_state_idx ON tianji_process.tianji_process_draft
                  (tenant_id,owner_type,owner_issuer,owner_id,state,created_at DESC,draft_id DESC)
                """);
        verifier.verifyAutomationV14();

        String admissionCheck = owner.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE
                    conrelid='tianji_process.tianji_process_instance_binding'::regclass AND conname='instance_binding_admission_check'
                """, String.class);
        assertNotNull(admissionCheck);
        String changed = admissionCheck.replace("admitted_at <= created_at", "admitted_at::text <= created_at::text");
        assertTrue(!changed.equals(admissionCheck));
        owner.execute("ALTER TABLE " + SCHEMA + "." + BINDING + " DROP CONSTRAINT instance_binding_admission_check");
        owner.execute("ALTER TABLE " + SCHEMA + "." + BINDING + " ADD CONSTRAINT instance_binding_admission_check " + changed);
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        owner.execute("ALTER TABLE " + SCHEMA + "." + BINDING + " DROP CONSTRAINT instance_binding_admission_check");
        owner.execute("ALTER TABLE " + SCHEMA + "." + BINDING + " ADD CONSTRAINT instance_binding_admission_check " + admissionCheck);
        verifier.verifyAutomationV14();

        owner.execute("ALTER TABLE " + SCHEMA + "." + NODE + " DROP CONSTRAINT node_execution_recovery_check");
        assertThrows(IllegalStateException.class, verifier::verifyAutomationV14);
        verifier.verify();
        verifier.verifyGovernedProcessStart();
    }

    private static void assertStoredAutomationGroups(JdbcTemplate runtime) {
        String hash = "a".repeat(64);
        String blank = """
                {"models":null,"inputSchemaSource":null,"outputSchemaSource":null,"nodeBindingsSource":null,"eventRoutesSource":null}
                """;
        String draftInsert = """
                INSERT INTO tianji_process.tianji_process_draft
                  (tenant_id,draft_id,definition_key,title,owner_type,owner_issuer,owner_id,state,revision,
                   content_json,content_sha256,last_command_id,created_at,updated_at)
                VALUES (1,?,'root','Draft','USER',?,'owner','EDITING',1,?::jsonb,?,'create',statement_timestamp(),statement_timestamp())
                """;
        assertEquals(1, runtime.update(draftInsert, "pd-1", hash, blank, hash));
        assertConstraint("process_draft_content_check", () -> runtime.update(draftInsert, "pd-2", hash, "{}", hash));
        assertConstraint("process_draft_base_check", () -> runtime.update("""
                UPDATE tianji_process.tianji_process_draft SET base_source_sha256=? WHERE tenant_id=1 AND draft_id='pd-1'
                """, hash));
        assertEquals(1, runtime.update("""
                INSERT INTO tianji_process.tianji_process_release
                  (tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,created_at)
                VALUES (1,'release','ACTIVE',?,'USER','owner',statement_timestamp())
                """, hash));
        assertEquals(1, runtime.update("""
                INSERT INTO tianji_process.tianji_process_automation_release
                  (tenant_id,release_key,root_definition_key,source_descriptor,source_sha256,compiler_version,compiled_sha256,
                   created_at,source_draft_id,source_draft_revision,draft_content_sha256,dependency_manifest,dependency_sha256)
                VALUES (1,'release','root','{}',?,'compiler',?,statement_timestamp(),'pd-1',1,?,'{}',?)
                """, hash, hash, hash, hash));
        String activate = """
                INSERT INTO tianji_process.tianji_process_automation_activation
                  (tenant_id,definition_key,revision,release_key,previous_revision,actor_type,actor_issuer,actor_id,command_id,
                   request_sha256,source_sha256,compiled_sha256,dependency_sha256,authorization_sha256,dependency_evidence_sha256,
                   release_signature_sha256,trust_generation,admission_proof,observed_at,proof_valid_until,activated_at)
                VALUES (1,'root',?,'release',?,'USER',?,'owner',?,?, ?,?,?, ?,?,?, 1,'{}',
                    statement_timestamp(),statement_timestamp()+interval '5 minutes',statement_timestamp())
                """;
        assertEquals(1, runtime.update(activate, 1L, null, hash, "activate", hash, hash, hash, hash, hash, hash, hash));
        assertConstraint("automation_activation_previous_check", () -> runtime.update(activate,
                2L, null, hash, "activate-2", hash, hash, hash, hash, hash, hash, hash));
        assertConstraint("activation_previous_fk", () -> runtime.update(activate,
                3L, 2L, hash, "activate-3", hash, hash, hash, hash, hash, hash, hash));

        assertEquals(1, runtime.update("""
                INSERT INTO tianji_process.tianji_process_command_receipt
                  (tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id,request_sha256,release_key,
                   correlation_id,state,resource_id,created_at)
                VALUES (1,'USER',?,'owner','START','start',?,'release','correlation','APPLIED','instance',statement_timestamp())
                """, hash, hash));
        String bind = """
                INSERT INTO tianji_process.tianji_process_instance_binding
                  (tenant_id,instance_id,release_key,definition_key,owner_type,owner_issuer,owner_id,profile,configuration,
                   configuration_sha256,start_request_sha256,created_at,activation_revision,source_command_type,source_command_id,
                   resolved_start_sha256,admission_kind,source_event_receipt_id,admission_evidence,admitted_at)
                VALUES (1,?,'release','root','USER',?,?,'AUTOMATION_V2','{}',?,?,statement_timestamp(),
                    1,'START','start',?,? ,?,'{}',statement_timestamp())
                """;
        assertEquals(1, runtime.update(bind, "instance", hash, "owner", hash, hash, hash, "DIRECT", null));
        assertConstraint("instance_start_fk", () -> runtime.update(bind, "outside-owner", hash, "other-owner",
                hash, hash, hash, "DIRECT", null));
        assertConstraint("instance_binding_admission_check", () -> runtime.update(bind, "bad-event", hash, "other-owner",
                hash, hash, hash, "EVENT", null));

        String nodeInsert = """
                INSERT INTO tianji_process.tianji_process_node_execution
                  (tenant_id,instance_id,occurrence_id,attempt,node_execution_ref,node_id,kind,parent_scope_id,attempt_scope_id,
                   capability_key,capability_version,effect_key,input_json,input_sha256,input_schema_sha256,output_schema_sha256,
                   configuration_sha256,state,revision,side_effect_outcome,started_at,node_deadline_at,attempt_deadline_at,
                   retry_command_id,retry_request_sha256,worker_effect_recovery)
                VALUES (1,'instance',?,?,?,'node','SERVICE','scope','attempt-scope','tool',1,'effect','{}',? ,?,?,?,
                    'SCHEDULED',1,'NONE',statement_timestamp(),statement_timestamp()+interval '5 minutes',
                    statement_timestamp()+interval '2 minutes',?,?,?::jsonb)
                """;
        assertEquals(1, runtime.update(nodeInsert, "ordinary", 1, "ref-1", hash, hash, hash, hash, null, null, null));
        String recovery = """
                {"mode":"REPLAY_APPLIED","observedGeneration":1,"observedAt":"2026-11-11T10:00:00Z",
                 "validUntil":"2026-11-11T10:02:00Z","authorityRef":"worker","evidenceSha256":"%s",
                 "businessReceiptId":"business","resultSha256":"%s"}
                """.formatted(hash, hash);
        assertEquals(1, runtime.update(nodeInsert, "ordinary", 2, "ref-2", hash, hash, hash, hash, "retry", hash, recovery));
        assertConstraint("node_execution_recovery_check", () -> runtime.update(nodeInsert,
                "bad-mode", 2, "ref-bad", hash, hash, hash, hash, "retry-2", hash, recovery.replace("\"REPLAY_APPLIED\"", "null")));
        assertConstraint("node_execution_recovery_check", () -> runtime.update(nodeInsert,
                "bad-result", 2, "ref-other", hash, hash, hash, hash, "retry-2", hash, recovery.replace("\"business\"", "null")));
    }

    private static void assertConstraint(String expected, Runnable action) {
        var failed = assertThrows(DataIntegrityViolationException.class, action::run);
        var postgres = org.junit.jupiter.api.Assertions.assertInstanceOf(PSQLException.class, failed.getMostSpecificCause());
        assertNotNull(postgres.getServerErrorMessage());
        assertEquals(expected, postgres.getServerErrorMessage().getConstraint());
    }

    private static void assertManifest() throws Exception {
        try (var manifest = AutomationProcessSchemaPostgresTest.class.getResourceAsStream("/db/tianji-process/manifest.json");
             var sql = AutomationProcessSchemaPostgresTest.class.getResourceAsStream("/db/tianji-process/" + V14)) {
            assertNotNull(manifest);
            assertNotNull(sql);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.readAllBytes()));
            var mapper = JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
            assertEquals(mapper.valueToTree(Map.of("schemaVersion", 1, "scripts",
                    List.of(Map.of("version", 14, "file", V14, "sha256", digest)))), mapper.readTree(manifest));
        }
    }

    private static void grant(JdbcTemplate jdbc, String quotedRole, String grants, String... tables) {
        for (String table : tables) jdbc.execute("GRANT " + grants + " ON " + SCHEMA + "." + table + " TO " + quotedRole);
    }

    private static DriverManagerDataSource source(String url, String role) {
        var source = new DriverManagerDataSource(url);
        if (role != null) source.setUsername(role);
        var settings = new Properties();
        settings.setProperty("connectTimeout", "5");
        settings.setProperty("socketTimeout", "15");
        source.setConnectionProperties(settings);
        return source;
    }

    private static Flyway process(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").createSchemas(false).baselineOnMigrate(false)
                .locations("classpath:db/tianji-process").target(target).cleanDisabled(true).load();
    }
}
