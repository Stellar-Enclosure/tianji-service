package top.egon.cola.platform.tianji.worker;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(45)
class WorkerEffectReceiptIT {
    static final long TENANT = 42;
    static final String SOURCE = "process-east";
    static final ProcessSchemaVO EMPTY = ProcessSchemaVO.fromWire(Map.of(
            "type", "object", "properties", Map.of(), "required", List.of(), "additionalProperties", false));
    private static FixtureVO fixture;

    @BeforeAll
    static void provision() {
        fixture = provision("tianji.worker.business.postgres.test.url");
    }

    @Test
    void absent_effect_is_serialized_across_jobs_and_lease_tokens_before_business_sql() throws Exception {
        var handlerCalls = new AtomicInteger();
        var descriptor = descriptor(WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, null);
        var registration = new AutomationWorkerHandlerRegistration(descriptor, EMPTY, EMPTY, (context, jdbc) -> {
            handlerCalls.incrementAndGet();
            jdbc.update("INSERT INTO business.effect_probe(effect_key) VALUES (?)", context.effectKey());
            return result("business-one");
        }, null);
        var remote1 = task(fixture, descriptor, "job-one", "serial-effect", 1, null);
        var remote2 = task(fixture, descriptor, "job-two", "serial-effect", 2, null);
        assertNotEquals(remote1.leaseTokenSha256(), remote2.leaseTokenSha256());
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return fixture.store().executeAutomation(remote1, registration);
            });
            var second = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return fixture.store().executeAutomation(remote2, registration);
            });
            var left = first.get(12, TimeUnit.SECONDS);
            var right = second.get(12, TimeUnit.SECONDS);
            assertEquals(Map.of(), left.typedOutput());
            assertEquals(left.typedOutput(), right.typedOutput());
            assertNotEquals(left.replayed(), right.replayed());
            assertEquals(left.businessReceiptId(), right.businessReceiptId());
            assertEquals(1, handlerCalls.get());
            assertEquals(1, fixture.owner().queryForObject(
                    "SELECT count(*) FROM business.effect_probe WHERE effect_key='serial-effect'", Integer.class));
            assertEquals(AutomationWorkerBusinessResultDTO.resultSha256(remote1.getEffectFingerprint(),
                    "business-one", Map.of()), fixture.owner().queryForObject(
                    "SELECT result_sha256 FROM business.tianji_worker_effect_receipt WHERE effect_key='serial-effect'", String.class));
        }
    }

    @Test
    void output_schema_failure_rolls_back_effect_and_business_sql_and_allows_same_key_to_succeed() {
        var descriptor = descriptor(WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, null);
        var remote = task(fixture, descriptor, "job-bad-output", "bad-output", 3, null);
        var attempts = new AtomicInteger();
        var registration = new AutomationWorkerHandlerRegistration(descriptor, EMPTY, EMPTY, (context, jdbc) -> {
            jdbc.update("INSERT INTO business.effect_probe(effect_key) VALUES (?)", context.effectKey());
            if (attempts.incrementAndGet() == 1) return new AutomationWorkerBusinessResultDTO("bad", Map.of("unexpected", true));
            return result("recovered");
        }, null);
        var store = fixture.store();
        assertThrows(IllegalArgumentException.class, () -> store.executeAutomation(remote, registration));
        assertEquals(1, attempts.get());
        assertEquals(0, fixture.owner().queryForObject(
                "SELECT count(*) FROM business.effect_probe WHERE effect_key='bad-output'", Integer.class));
        assertEquals(0, fixture.owner().queryForObject(
                "SELECT count(*) FROM business.tianji_worker_effect_receipt WHERE effect_key='bad-output'", Integer.class));
        assertFalse(store.executeAutomation(remote, registration).replayed());
    }

    @Test
    void stable_effect_fingerprint_has_independent_wire_goldens() {
        var input = Map.<String, Object>of("amount", "1.00", "customer", "处理");
        var tx = new AutomationWorkerHandlerDescriptor("customer.check", 1, "a".repeat(64), "b".repeat(64),
                WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, "handler-v1", null);
        var external = new AutomationWorkerHandlerDescriptor("customer.check", 1, "a".repeat(64), "b".repeat(64),
                WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, "handler-v1", "customer-system");
        assertEquals("838e80851322bf85f3dcdbe05ac20d799392470436b8c2283dfce12d3c3c5a4b",
                WorkerDigests.automationEffectFingerprint(tx, input));
        assertEquals("3ef2bbe9d0d83594867f37ae5a0b10d2ba499c0058a16b47fe5ddf3dbadec029",
                WorkerDigests.automationEffectFingerprint(external, input));
    }

    @Test
    void logical_worker_key_is_checked_by_the_handler_and_physical_receipt() {
        var baseline = descriptor(WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, null);
        assertThrows(IllegalArgumentException.class, () -> new AutomationWorkerHandlerDescriptor(
                "customer:check", 1, baseline.inputSchemaSha256(), baseline.outputSchemaSha256(),
                baseline.effectMode(), baseline.handlerVersion(), null));
        var descriptor = new AutomationWorkerHandlerDescriptor("_customer.check", 1, baseline.inputSchemaSha256(),
                baseline.outputSchemaSha256(), baseline.effectMode(), baseline.handlerVersion(), null);
        var registered = new AutomationWorkerHandlerRegistration(descriptor, EMPTY, EMPTY,
                (context, operations) -> result("logical-worker-key"), null);
        var acquired = task(fixture, descriptor, "job-key", "key-alphabet", 33, null);

        assertEquals("logical-worker-key", fixture.store().executeAutomation(acquired, registered).businessReceiptId());
        assertEquals("_customer.check", fixture.owner().queryForObject("""
                SELECT capability_key FROM business.tianji_worker_effect_receipt WHERE effect_key='key-alphabet'
                """, String.class));
        assertThrows(DataIntegrityViolationException.class, () -> fixture.owner().update("""
                UPDATE business.tianji_worker_effect_receipt SET capability_key='customer:check'
                WHERE effect_key='key-alphabet'
                """));
    }

    @Test
    void exact_v3_history_role_and_schema_drift_fail_closed() {
        var owner = fixture.owner();
        var verifier = new WorkerExecutionSchemaVerifier(fixture.runtime(), "business");
        var ddlTx = new org.springframework.transaction.support.TransactionTemplate(
                new DataSourceTransactionManager((DriverManagerDataSource) owner.getDataSource()));
        var role = fixture.runtime().getUsername();
        verifier.verifyEffectsV3();
        try {
            owner.update("UPDATE business.tianji_worker_schema_history SET checksum=checksum+1 WHERE version='3'");
            assertThrows(IllegalStateException.class, verifier::verifyEffectsV3);
        } finally {
            owner.update("UPDATE business.tianji_worker_schema_history SET checksum=checksum-1 WHERE version='3'");
        }
        try {
            owner.execute("GRANT DELETE ON business.tianji_worker_effect_receipt TO " + role);
            assertThrows(IllegalStateException.class, verifier::verifyEffectsV3);
        } finally {
            owner.execute("REVOKE DELETE ON business.tianji_worker_effect_receipt FROM " + role);
        }
        try {
            owner.execute("ALTER TABLE business.tianji_worker_effect_receipt ADD CONSTRAINT effect_extra CHECK (true)");
            assertThrows(IllegalStateException.class, verifier::verifyEffectsV3);
        } finally {
            owner.execute("ALTER TABLE business.tianji_worker_effect_receipt DROP CONSTRAINT effect_extra");
        }
        verifier.verifyEffectsV3();
        ddlTx.executeWithoutResult(status -> {
            owner.execute("SET LOCAL enable_seqscan=off");
            var plan = owner.queryForList("""
                    EXPLAIN (COSTS OFF) SELECT state FROM business.tianji_worker_effect_receipt
                    WHERE source_id='process-east'::varchar AND tenant_id=42 AND effect_key='any-effect'::varchar
                    """, String.class);
            assertTrue(plan.stream().anyMatch(line -> line.contains("tianji_worker_effect_receipt_pkey")), plan.toString());
        });
    }

    @Test
    void result_groups_require_all_fields_and_canonical_size_ignores_only_rendering_spaces() {
        var owner = fixture.owner();
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new DataSourceTransactionManager((DriverManagerDataSource) owner.getDataSource()));
        var descriptor = descriptor(WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, "customer-system");
        String hash = "a".repeat(64);
        String insert = """
                INSERT INTO business.tianji_worker_effect_receipt
                  (source_id,tenant_id,effect_key,capability_key,capability_version,input_schema_sha256,business_input_sha256,
                   state,result_json,business_receipt_id,revision,created_at,updated_at,
                   output_schema_sha256,effect_mode,handler_version,target_id,effect_fingerprint,result_sha256,dispatch_generation,
                   last_reconciliation,reconciled_at)
                VALUES (?,?,'physical-constraints',?,1,?,?,?,?::jsonb,?,1,statement_timestamp(),statement_timestamp(),
                        ?,'EXTERNAL_IDEMPOTENT','handler-v1','customer-system',?,?,1,?::jsonb,?)
                """;
        var json = new LinkedHashMap<String, Object>();
        json.put("ordered", "引号、带引号“\" x \n末尾”");
        json.put("a", " x ".repeat(150));
        int padding = 65_536 - ProcessTypedJson.canonicalBytes(Map.of(
                "ordered", json.get("ordered"), "a", json.get("a"), "p", "")).length;
        json.put("p", "n".repeat(padding));
        String accepted = new String(ProcessTypedJson.canonicalBytes(json), StandardCharsets.UTF_8);
        assertEquals(65_536, accepted.getBytes(StandardCharsets.UTF_8).length);
        tx.executeWithoutResult(status -> {
            assertEquals(1, owner.update(insert, SOURCE, TENANT, descriptor.capabilityKey(),
                    descriptor.inputSchemaSha256(), hash, "COMPLETED", accepted, "proof-receipt",
                    descriptor.outputSchemaSha256(), hash, hash, null, null));
            status.setRollbackOnly();
        });
        assertThrows(DataIntegrityViolationException.class, () -> owner.update(insert, SOURCE, TENANT,
                descriptor.capabilityKey(), descriptor.inputSchemaSha256(), hash, "COMPLETED",
                accepted.replace("nnnn", "nnnnn"), "proof-receipt", descriptor.outputSchemaSha256(), hash, hash, null, null));
        assertThrows(DataIntegrityViolationException.class, () -> owner.update(insert, SOURCE, TENANT,
                descriptor.capabilityKey(), descriptor.inputSchemaSha256(), hash, "STARTED", null, "partial-unfinished",
                descriptor.outputSchemaSha256(), hash, null, null, null));
        assertThrows(DataIntegrityViolationException.class, () -> owner.update(insert, SOURCE, TENANT,
                descriptor.capabilityKey(), descriptor.inputSchemaSha256(), hash, "COMPLETED", "{}", null,
                descriptor.outputSchemaSha256(), hash, hash, null, null));
        assertThrows(DataIntegrityViolationException.class, () -> owner.update(insert, SOURCE, TENANT,
                descriptor.capabilityKey(), descriptor.inputSchemaSha256(), hash, "UNKNOWN", null, null,
                descriptor.outputSchemaSha256(), hash, null, "{}", null));
    }

    static FixtureVO provision(String property) {
        String url = System.getProperty(property, "");
        String role = System.getProperty("tianji.worker.business.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !role.matches("tianji_it_worker_[0-9a-f]{12}")
                || System.getProperty("tianji.worker.business.postgres.test.url", "").equals(
                        System.getProperty("tianji.worker.nontransactional.postgres.test.url", ""))) {
            throw new IllegalArgumentException("Separate explicit loopback worker databases and restricted role are required");
        }
        var ownerSource = new DriverManagerDataSource(url);
        var owner = new JdbcTemplate(ownerSource);
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='business'", Integer.class));
        owner.execute("CREATE SCHEMA business");
        assertEquals(2, flyway(ownerSource, "2").migrate().migrationsExecuted);
        var prefix = owner.queryForList("""
                SELECT version,success,checksum FROM business.tianji_worker_schema_history ORDER BY installed_rank
                """);
        owner.execute("CREATE TABLE business.effect_probe(effect_key text PRIMARY KEY)");
        owner.execute("GRANT USAGE ON SCHEMA business TO " + role);
        owner.execute("GRANT SELECT ON business.tianji_worker_schema_history TO " + role);
        owner.execute("GRANT SELECT,INSERT ON business.tianji_worker_execution_receipt,business.tianji_worker_nontransactional_intent,business.effect_probe TO " + role);
        if (WorkerEffectReceiptIT.class.getResource("/db/tianji-worker/V3__add_worker_effect_receipt.sql") != null) {
            var v3 = flyway(ownerSource, "3");
            assertEquals(1, v3.migrate().migrationsExecuted);
            v3.validate();
            verifyManifest();
            assertEquals(prefix, owner.queryForList("""
                    SELECT version,success,checksum FROM business.tianji_worker_schema_history WHERE version<>'3' ORDER BY installed_rank
                    """));
            owner.execute("GRANT SELECT,INSERT,UPDATE ON business.tianji_worker_effect_receipt TO " + role);
        }
        var runtime = new DriverManagerDataSource(url + "?currentSchema=business");
        runtime.setUsername(role);
        assertTrue(Boolean.TRUE.equals(new JdbcTemplate(runtime).queryForObject("""
                SELECT current_user=? AND NOT has_schema_privilege('business','CREATE')
                  AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolsuper AND NOT rolbypassrls
                FROM pg_roles WHERE rolname=current_user
                """, Boolean.class, role)));
        new WorkerExecutionSchemaVerifier(runtime, "business").verifyNonTransactional();
        return new FixtureVO(owner, runtime);
    }

    private static void verifyManifest() {
        var loader = WorkerEffectReceiptIT.class.getClassLoader();
        try (var sql = loader.getResourceAsStream("db/tianji-worker/V3__add_worker_effect_receipt.sql");
             var manifest = loader.getResourceAsStream("db/tianji-worker/manifest.json")) {
            var mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
            assertTrue(sql != null && manifest != null);
            assertEquals(mapper.valueToTree(Map.of("schemaVersion", 1, "scripts", List.of(Map.of(
                    "version", 3, "file", "V3__add_worker_effect_receipt.sql", "sha256", sha256(sql.readAllBytes()))))),
                    mapper.readTree(manifest));
        } catch (java.io.IOException unreadable) {
            throw new AssertionError(unreadable);
        }
    }

    private static Flyway flyway(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas("business").defaultSchema("business")
                .table("tianji_worker_schema_history").createSchemas(false).target(target)
                .locations("classpath:db/tianji-worker").cleanDisabled(true).load();
    }

    static AutomationWorkerHandlerDescriptor descriptor(WorkerEffectSafetyEnum mode, String target) {
        String schemaSha = sha256(ProcessTypedJson.canonicalBytes(EMPTY.toWire()));
        return new AutomationWorkerHandlerDescriptor("customer.check", 1, schemaSha, schemaSha, mode, "handler-v1", target);
    }

    static AutomationWorkerTaskDTO task(FixtureVO fixture, AutomationWorkerHandlerDescriptor descriptor, String job,
                                        String effect, int seed, WorkerEffectRecoveryVO recovery) {
        var now = fixture.now();
        var token = new byte[32];
        token[0] = (byte) seed;
        var input = Map.<String, Object>of();
        var task = AutomationWorkerTaskDTO.builder().taskId(job).activityOccurrenceId("occurrence-one")
                .nodeExecutionRef("node-execution").leaseToken(Base64.getUrlEncoder().withoutPadding().encodeToString(token))
                .capabilityKey(descriptor.capabilityKey()).capabilityVersion(descriptor.capabilityVersion())
                .input(input).inputSha256(sha256(ProcessTypedJson.canonicalBytes(input))).effectKey(effect)
                .attemptDeadlineAt(now.plusSeconds(30)).leaseExpiresAt(now.plusSeconds(20)).handlerVersion(descriptor.handlerVersion())
                .effectMode(descriptor.effectMode()).targetId(descriptor.targetId()).inputSchemaSha256(descriptor.inputSchemaSha256())
                .outputSchemaSha256(descriptor.outputSchemaSha256()).effectFingerprint(fingerprint(descriptor, input))
                .effectRecovery(recovery).build();
        return task.setTaskContractSha256(task.contractSha256(TENANT, SOURCE));
    }

    static String fingerprint(AutomationWorkerHandlerDescriptor descriptor, Map<String, Object> input) {
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_EFFECT_V2", bytes(descriptor.capabilityKey()),
                bytes(Long.toString(descriptor.capabilityVersion())), bytes(descriptor.inputSchemaSha256()),
                bytes(descriptor.outputSchemaSha256()), bytes(descriptor.effectMode().name()),
                bytes(descriptor.handlerVersion()), ProcessTypedJson.canonicalBytes(descriptor.targetId()),
                ProcessTypedJson.canonicalBytes(input));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static AutomationWorkerBusinessResultDTO result(String receipt) {
        return new AutomationWorkerBusinessResultDTO(receipt, Map.of());
    }

    record FixtureVO(JdbcTemplate owner, DriverManagerDataSource runtime) {
        Instant now() {
            return owner.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
        }

        JdbcWorkerExecutionStore store() {
            return store(null);
        }

        JdbcWorkerExecutionStore store(WorkerEffectOutcomeResolver resolver) {
            return new JdbcWorkerExecutionStore(runtime, new DataSourceTransactionManager(runtime), "business", SOURCE,
                    TENANT, Duration.ofSeconds(10), Duration.ofSeconds(1), resolver);
        }
    }
}
