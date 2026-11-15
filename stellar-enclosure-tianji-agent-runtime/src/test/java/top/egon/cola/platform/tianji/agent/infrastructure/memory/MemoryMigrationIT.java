package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.agentflow.api.memory.MemoryScopeVO;
import top.egon.cola.component.agentflow.memory.jdbc.JdbcMemoryStore;
import top.egon.cola.component.agentflow.memory.jdbc.MemoryStoreStorageVerifier;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(120)
class MemoryMigrationIT {
    private static final String AF = "egon_agent_memory";
    private static final String LEGACY = "tianji_agent";
    private static final Clock CLOCK = Clock.systemUTC();
    private static final TrustedAgentActor ALICE = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/migration");
    private static final TrustedAgentActor BOB = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/migration");
    private static final MemoryOwnerDTO TEAM = new MemoryOwnerDTO(MemoryOwnerTypeEnum.TEAM, "迁移".repeat(128));
    private static final String SCOPE = "范围".repeat(64);
    private static final MemorySourceDTO PROOF = new MemorySourceDTO("old-explicit-source", "a".repeat(64), null, null);
    private static final MemoryRetentionPolicy POLICY = new MemoryRetentionPolicy("migration", 1, Duration.ofDays(1), Duration.ofDays(30),
            1000, Set.of(MemorySensitivityEnum.CONFIDENTIAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);
    private static final JsonMapper EVENT_JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).addModule(new JavaTimeModule()).build();
    private static final String CHANNEL = "tianji-memory-index";
    private static final String DESTINATION = "agent-memory";
    private static final String HEADER = "tianji-memory-index-fingerprint";
    private final List<MemoryIndexPublication.Event> events = new CopyOnWriteArrayList<>();
    private MemoryIndexPublication.Binding binding;
    private VersionedMemoryEmbeddingModel embedding;

    private DriverManagerDataSource source;
    private DriverManagerDataSource maintenance;
    private JdbcTemplate privileged;
    private String migrationRole;
    private String version;

    @BeforeAll
    void isolatedStores() throws Exception {
        String url = System.getProperty("tianji.memory.migration.postgres.test.url");
        String runtimeRole = System.getProperty("tianji.memory.migration.postgres.test.runtime-user");
        assertNotNull(url, "An explicitly isolated migration database is required");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}"));
        assertNotNull(runtimeRole);
        assertTrue(runtimeRole.matches("tianji_it_runtime_[0-9a-f]{12}"));
        String maintenanceRole = System.getProperty("tianji.memory.migration.postgres.test.maintenance-user");
        assertNotNull(maintenanceRole);
        assertTrue(maintenanceRole.matches("tianji_it_maintenance_[0-9a-f]{12}"));
        migrationRole = System.getProperty("tianji.memory.migration.postgres.test.migration-user", runtimeRole + "_migration");
        assertTrue(migrationRole.matches("tianji_it_(?:runtime_[0-9a-f]{12}_migration|migration_[0-9a-f]{12})"));
        version = System.getProperty("tianji.memory.postgres.vector.version");
        assertNotNull(version);
        assertTrue(version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"));

        var admin = new DriverManagerDataSource(url);
        privileged = new JdbcTemplate(admin);
        assertEquals(0, privileged.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname IN ('tianji_agent','egon_agent_memory')", Integer.class));
        assertEquals(version, privileged.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
        assertEquals(17, flyway(admin, "17").migrate().migrationsExecuted);
        privileged.execute("CREATE SCHEMA " + AF);
        new ResourceDatabasePopulator(new ClassPathResource("db/egon-agent-memory/V1__create_memory_store.sql")).execute(admin);
        String ddl = System.getProperty("tianji.memory.migration.postgres.test.outbox-ddl");
        assertNotNull(ddl, "Provide the existing Components Outbox V1 DDL");
        var ddlPath = java.nio.file.Path.of(ddl);
        assertEquals("V1__create_transactional_outbox_schema.sql", ddlPath.getFileName().toString());
        assertTrue(ddlPath.isAbsolute() && java.nio.file.Files.isRegularFile(ddlPath));
        assertEquals("611d47e890618364c6c89d2967c4159ba5bde02756c52165799a6d6a0ca6b7f8",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(ddlPath))));
        var outbox = new ResourceDatabasePopulator(new FileSystemResource(ddlPath));
        var install = new DriverManagerDataSource(url + "?currentSchema=" + LEGACY);
        outbox.execute(install);
        privileged.execute("GRANT USAGE ON SCHEMA tianji_agent,egon_agent_memory TO " + runtimeRole + "," + migrationRole);
        privileged.execute("GRANT SELECT ON tianji_agent.agent_memory_scope,tianji_agent.agent_memory_entry,"
                + "tianji_agent.agent_memory_revision,tianji_agent.agent_memory_command_receipt,tianji_agent.agent_memory_index_manifest,"
                + "tianji_agent.agent_memory_vector_v1,tianji_agent.agent_memory_preference_intent,tianji_agent.egon_cola_outbox_message TO " + migrationRole);
        privileged.execute("GRANT UPDATE (index_state,deleted_at,updated_at) ON tianji_agent.agent_memory_index_manifest TO " + migrationRole);
        privileged.execute("GRANT DELETE ON tianji_agent.agent_memory_vector_v1 TO " + migrationRole);
        privileged.execute("GRANT SELECT,INSERT ON ALL TABLES IN SCHEMA egon_agent_memory TO " + migrationRole);
        privileged.execute("GRANT UPDATE ON egon_agent_memory.scope TO " + migrationRole);
        privileged.execute("GRANT DELETE ON egon_agent_memory.revision,egon_agent_memory.fact,egon_agent_memory.receipt TO " + migrationRole);
        privileged.execute("GRANT SELECT ON ALL TABLES IN SCHEMA tianji_agent,egon_agent_memory TO " + runtimeRole);
        privileged.execute("GRANT INSERT,UPDATE ON tianji_agent.agent_memory_scope,tianji_agent.agent_memory_entry,"
                + "tianji_agent.agent_memory_revision,tianji_agent.agent_memory_command_receipt,tianji_agent.agent_memory_index_manifest,"
                + "tianji_agent.agent_memory_vector_v1,tianji_agent.agent_memory_run_grant TO " + runtimeRole);
        privileged.execute("GRANT INSERT ON tianji_agent.egon_cola_outbox_message TO " + runtimeRole);
        privileged.execute("GRANT USAGE ON SEQUENCE tianji_agent.egon_cola_outbox_message_id_seq TO " + runtimeRole);
        privileged.execute("GRANT DELETE ON tianji_agent.agent_memory_vector_v1 TO " + runtimeRole);
        privileged.execute("GRANT INSERT ON tianji_agent.agent_context_snapshot TO " + runtimeRole);
        privileged.execute("GRANT INSERT ON ALL TABLES IN SCHEMA egon_agent_memory TO " + runtimeRole);
        privileged.execute("GRANT UPDATE ON egon_agent_memory.scope,egon_agent_memory.fact TO " + runtimeRole);
        privileged.execute("GRANT UPDATE (state,content,provenance,redacted_at,host_metadata) ON egon_agent_memory.revision TO " + runtimeRole);
        source = new DriverManagerDataSource(url);
        source.setUsername(runtimeRole);
        maintenance = new DriverManagerDataSource(url);
        maintenance.setUsername(maintenanceRole);
        var delegate = mock(EmbeddingModel.class);
        when(delegate.call(any(EmbeddingRequest.class))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            var metadata = new EmbeddingResponseMetadata();
            metadata.setModel("text-embedding-3-small");
            return new EmbeddingResponse(List.of(new Embedding(new float[]{0.25f, 0.75f}, 0)), metadata);
        });
        var profile = new VersionedMemoryEmbeddingModel.Profile("migration", 1, "embeddingBean", "text-embedding-3-small", 2, 8191, 64, 16);
        embedding = new VersionedMemoryEmbeddingModel(profile, delegate,
                () -> EmbeddingOptions.builder().model("text-embedding-3-small").dimensions(2).build());
        binding = new MemoryIndexPublication.Binding(embedding.descriptor().modelRef(), 2, "agent_memory_vector_v1");
    }

    @Test
    void switchesOriginalCommandDomainsAndFullHistoryAtOneFrozenEpoch() {
        var large = new MemoryContentDTO("\\".repeat(10_000), Map.of("known", "original"));
        assertTrue(new MemoryJsonCodec().content(large).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16 * 1024);
        String key = "键".repeat(128);
        var first = upsert(key, "fact", large);
        var legacy = new PostgresMemoryService(new AgentLedgerService(source), source, ignored -> MemoryAuthorization.Decision.ALLOW,
                List.of(POLICY), publication(), CLOCK);
        legacy.verifyStorage(2, version);
        var original = legacy.upsert(context(ALICE), first);
        assertEquals(1L, original.revision());

        var admin = new DriverManagerDataSource(System.getProperty("tianji.memory.migration.postgres.test.url"));
        assertEquals(1, flyway(admin, "18").migrate().migrationsExecuted);
        flyway(admin, "18").validate();

        new MemoryStoreStorageVerifier(source, AF).verify();
        try (var validator = Validation.buildDefaultValidatorFactory();
             var index = new PgVectorMemoryIndex(source, binding, embedding, 2, CLOCK);
             var maintenanceIndex = new PgVectorMemoryIndex(maintenance, binding, embedding, 2, CLOCK)) {
            var store = new JdbcMemoryStore(source, AF, "COMPONENT", CLOCK, validator.getValidator());
            var adapter = new LegacyMemoryStoreAdapter(source, AF, store, CLOCK);
            var memory = new PostgresMemoryService(new AgentLedgerService(source), source, ignored -> MemoryAuthorization.Decision.ALLOW,
                    List.of(POLICY), publication(source, adapter), CLOCK, index, adapter);
            memory.verifyStorage(2, version);
            var secondActor = memory.upsert(context(BOB), first);
            assertEquals("DEDUPLICATED", secondActor.outcome().name());
            var scope = LegacyMemoryStoreAdapter.scopeOf(ALICE.tenantId(), ALICE.issuerFingerprint(), TEAM, SCOPE);
            assertEquals(1L, privileged.queryForObject("SELECT authority_epoch FROM egon_agent_memory.scope WHERE scope_hash=?", Long.class, scope.hash()));
            privileged.update("UPDATE egon_agent_memory.scope SET authority_epoch=3 WHERE scope_hash=?", scope.hash());
            var amended = new CorrectMemoryCommand(key, TEAM, SCOPE, "fact", 1,
                    new MemoryContentDTO("corrected", Map.of()), PROOF, BigDecimal.ONE, "REVIEWED");
            var corrected = memory.correct(context(ALICE), amended);
            assertEquals(2L, corrected.revision());

            var maintenanceStore = new JdbcMemoryStore(maintenance, AF, "COMPONENT", CLOCK, validator.getValidator());
            var maintenanceAdapter = new LegacyMemoryStoreAdapter(maintenance, AF, maintenanceStore, CLOCK);
            var maintenanceMemory = new PostgresMemoryService(new AgentLedgerService(maintenance), maintenance,
                    ignored -> MemoryAuthorization.Decision.ALLOW, List.of(POLICY), publication(maintenance, maintenanceAdapter), CLOCK, maintenanceIndex, maintenanceAdapter);
            maintenanceMemory.verifyStorage(2, version);
            var indexes = new MemoryIndexWork(maintenance, maintenanceMemory, maintenanceIndex, ignored -> { }, CLOCK);
            var service = new MemoryScopeMigrationService(maintenance, AF, maintenanceStore, indexes, migrationRole,
                    authority((selected, epoch) -> settleIndexes(indexes, SCOPE)), protocol(), CLOCK);
            var cut = service.freezeAndMigrate(scope, 3L, deadline());
            assertEquals("COMPONENT", cut.storageRef(), "the unchanged baseline cannot switch the authoritative scope");
            assertEquals(5L, cut.authorityEpoch());
            assertEquals(2L, cut.highWaterRevision());
            assertEquals(0, privileged.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1 WHERE metadata->>'scopeId'=?", Integer.class, SCOPE));
            assertEquals("DELETED", privileged.queryForObject("SELECT index_state FROM tianji_agent.agent_memory_index_manifest WHERE scope_id=?", String.class, SCOPE));
            assertEquals("COMPONENT", privileged.queryForObject("SELECT storage_ref FROM egon_agent_memory.scope WHERE scope_hash=?", String.class, scope.hash()));
            assertEquals(List.of("NULL", "1", "3"), privileged.queryForList("""
                    SELECT COALESCE(authority_epoch::text,'NULL') FROM egon_agent_memory.receipt
                    WHERE scope_hash=? ORDER BY authority_epoch NULLS FIRST
                    """, String.class, scope.hash()));
            assertEquals(new MemoryJsonCodec().contentSha256(large), privileged.queryForObject("""
                    SELECT content_sha256 FROM egon_agent_memory.revision WHERE scope_hash=? AND fact_id='fact' AND revision=1
                    """, String.class, scope.hash()));
            assertTrue(privileged.queryForObject("""
                    SELECT octet_length(content::text)>16384 FROM egon_agent_memory.revision WHERE scope_hash=? AND fact_id='fact' AND revision=1
                    """, Boolean.class, scope.hash()));
            assertEquals(original, memory.upsert(context(ALICE), first));
            assertEquals(secondActor, memory.upsert(context(BOB), first));
            assertEquals(corrected, memory.correct(context(ALICE), amended));
            var forgotten = memory.forget(context(ALICE), new ForgetMemoryCommand(key, TEAM, SCOPE, "fact", 2, "USER_FORGET"));
            assertEquals(3L, forgotten.revision());
            var rebuild = service.rebuildHeads(scope, cut.authorityEpoch(), null, 0, 1, deadline());
            assertEquals(3L, rebuild.highWaterRevision());
            assertEquals("FORGOTTEN", rebuild.heads().getFirst().state());
            assertFalse(rebuild.heads().getFirst().indexableNow());
            assertTrue(service.rebuildHeads(scope, cut.authorityEpoch(), rebuild.highWaterRevision(), rebuild.nextScopeRevision(), 1, deadline()).complete());
            assertEquals(MemoryStateEnum.FORGOTTEN, memory.get(context(ALICE), new GetMemoryQuery(TEAM, SCOPE, "fact")).state());
            assertEquals(0, privileged.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE scope_id=? AND content_json IS NOT NULL", Integer.class, SCOPE));
            assertEquals(0, privileged.queryForObject("SELECT count(*) FROM egon_agent_memory.revision WHERE scope_hash=? AND content IS NOT NULL", Integer.class, scope.hash()));
            assertEquals(original, memory.upsert(context(ALICE), first));

            var liveIndexWork = new MemoryIndexWork(source, memory, index, ignored -> { }, CLOCK);
            blockedPendingAndUnsafePhysicalCopies(memory, liveIndexWork, maintenanceStore, indexes);
            verifiedLargeScopeAndPartialRollback(memory, maintenanceStore, indexes);
            concurrentOwnersAndTail(memory, maintenanceMemory, maintenanceIndex, maintenanceStore, indexes);
            ambiguousCommittedSwitch(memory, validator.getValidator());
        }
    }

    private void blockedPendingAndUnsafePhysicalCopies(PostgresMemoryService memory, MemoryIndexWork liveIndex,
                                                       JdbcMemoryStore store, MemoryIndexWork index) {
        var operator = migration(maintenance, store, index, authority((scope, epoch) -> { }));
        var scoped = "blocked-index";
        var owner = TEAM;
        var original = fact("pending", owner, scoped, "victim", "protected vector", true);
        memory.upsert(context(ALICE), original);
        var scope = scope(ALICE, owner, scoped);

        assertThrows(AgentLedgerException.class, () -> operator.freezeAndMigrate(scope, 1, deadline()));
        assertLegacyActive(scope, 3);
        assertEquals(0, shadowCount(scope));
        settleIndexes(liveIndex, scoped);
        var documentId = privileged.queryForObject("SELECT document_id FROM tianji_agent.agent_memory_index_manifest WHERE scope_id=?", UUID.class, scoped);
        assertNotNull(documentId);
        privileged.update("""
                UPDATE tianji_agent.agent_memory_index_manifest SET index_state='DELETED',deleted_at=clock_timestamp(),updated_at=clock_timestamp()
                WHERE document_id=?
                """, documentId);
        var deletedAt = privileged.queryForObject("SELECT deleted_at FROM tianji_agent.agent_memory_index_manifest WHERE document_id=?",
                java.sql.Timestamp.class, documentId);
        privileged.update("UPDATE tianji_agent.agent_memory_vector_v1 SET metadata=jsonb_set(metadata,'{kind}','\"WRONG\"') WHERE id=?", documentId);
        assertThrows(AgentLedgerException.class, () -> operator.freezeAndMigrate(scope, 3, deadline()));
        assertLegacyActive(scope, 5);
        assertEquals(0, shadowCount(scope));
        assertEquals(1, privileged.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1 WHERE id=?", Integer.class, documentId));
        privileged.update("UPDATE tianji_agent.agent_memory_vector_v1 SET metadata=jsonb_set(metadata,'{kind}','\"FACT\"') WHERE id=?", documentId);

        UUID orphan = UUID.randomUUID();
        privileged.update("""
                INSERT INTO tianji_agent.agent_memory_vector_v1(id,content,metadata,embedding)
                SELECT ?,'orphan',metadata,'[1,0]'::public.vector FROM tianji_agent.agent_memory_vector_v1 WHERE id=?
                """, orphan, documentId);
        assertThrows(AgentLedgerException.class, () -> operator.freezeAndMigrate(scope, 5, deadline()));
        assertLegacyActive(scope, 7);
        assertEquals(0, shadowCount(scope));
        assertEquals(2, privileged.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1 WHERE id IN (?,?)",
                Integer.class, documentId, orphan), "rollback must restore a valid removed vector after proving an orphan exists");
        assertEquals(deletedAt, privileged.queryForObject("SELECT deleted_at FROM tianji_agent.agent_memory_index_manifest WHERE document_id=?",
                java.sql.Timestamp.class, documentId));

        privileged.update("DELETE FROM tianji_agent.agent_memory_vector_v1 WHERE id=?", orphan);
        assertEquals("COMPONENT", operator.freezeAndMigrate(scope, 7, deadline()).storageRef());
        assertEquals(0, privileged.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1 WHERE id=?", Integer.class, documentId));
        assertEquals(deletedAt, privileged.queryForObject("SELECT deleted_at FROM tianji_agent.agent_memory_index_manifest WHERE document_id=?",
                java.sql.Timestamp.class, documentId));
    }

    private void verifiedLargeScopeAndPartialRollback(PostgresMemoryService memory, JdbcMemoryStore store, MemoryIndexWork index) {
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "migration-app", "https://idp.example/migration");
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.APPLICATION, actor.subjectId());
        String scoped = "batch-history";
        var scope = scope(actor, owner, scoped);
        var tail = seedLargeHistory(actor, owner, scoped);
        var checks = new AtomicInteger();
        var failed = migration(maintenance, store, index, new MemoryScopeMigrationService.MigrationAuthority() {
            @Override public void authorize(MemoryScopeVO selected, Instant deadline) { }
            @Override public MemoryScopeMigrationService.DrainWitnessVO quiesce(MemoryScopeVO selected, long epoch, Instant deadline) {
                return witnessed(selected, epoch, deadline);
            }
            @Override public boolean current(MemoryScopeMigrationService.DrainWitnessVO witness) {
                return checks.incrementAndGet() < 3;
            }
        });
        assertThrows(AgentLedgerException.class, () -> failed.freezeAndMigrate(scope, 1, deadline()));
        assertLegacyActive(scope, 3);
        assertEquals(0, shadowCount(scope));
        assertEquals(1_000, privileged.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE scope_id=?", Integer.class, scoped));

        var operator = migration(maintenance, store, index, authority((selected, epoch) -> { }));
        var cut = operator.freezeAndMigrate(scope, 3, CLOCK.instant().plusSeconds(60));
        assertEquals(1_000L, cut.highWaterRevision());
        assertEquals("bulk-1000", memory.upsert(context(actor), tail).memoryId());
        var head = operator.rebuildHeads(scope, cut.authorityEpoch(), null, 0, 100, deadline());
        var total = 0;
        while (true) {
            total += head.heads().size();
            if (head.complete()) break;
            head = operator.rebuildHeads(scope, cut.authorityEpoch(), head.highWaterRevision(), head.nextScopeRevision(), 100, deadline());
        }
        assertEquals(1_000, total);
        assertEquals(1_000L, head.nextScopeRevision());
    }

    private UpsertMemoryCommand seedLargeHistory(TrustedAgentActor actor, MemoryOwnerDTO owner, String scoped) {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(privileged.getDataSource()));
        return tx.execute(status -> {
            var stamp = java.sql.Timestamp.from(CLOCK.instant().truncatedTo(ChronoUnit.MICROS));
            var expiry = java.sql.Timestamp.from(stamp.toInstant().plus(Duration.ofDays(1)));
            privileged.update("INSERT INTO tianji_agent.agent_memory_scope VALUES (?,?,?,?,?,?,?)", actor.tenantId(),
                    actor.issuerFingerprint(), owner.type().name(), owner.id(), scoped, stamp, stamp);
            var entries = new ArrayList<Object[]>();
            var versions = new ArrayList<Object[]>();
            var receipts = new ArrayList<Object[]>();
            var codec = new MemoryJsonCodec();
            UpsertMemoryCommand tail = null;
            for (int at = 1; at <= 1000; at++) {
                String id = String.format(Locale.ROOT, "bulk-%04d", at);
                tail = new UpsertMemoryCommand(id, owner, scoped, id, MemoryKindEnum.FACT, 0, new MemoryContentDTO(id, Map.of()),
                        PROOF, BigDecimal.ONE, true, MemorySensitivityEnum.PUBLIC, POLICY.reference(), null, null);
                String hash = codec.contentSha256(tail.content());
                Object[] key = {actor.tenantId(), actor.issuerFingerprint(), owner.type().name(), owner.id(), scoped};
                entries.add(values(key, id, hash, POLICY.reference(), binding.modelRef(), stamp, expiry, stamp, stamp));
                versions.add(values(key, id, codec.content(tail.content()), codec.source(PROOF), hash, PROOF.evidenceSha256(),
                        actor.identityFingerprint(), POLICY.reference(), binding.modelRef(), stamp, expiry, stamp));
                receipts.add(values(key, actor.subjectType().name(), actor.subjectId(), id, codec.commandSha256(tail), id, stamp));
            }
            privileged.batchUpdate("""
                    INSERT INTO tianji_agent.agent_memory_entry(tenant_id,owner_issuer,owner_type,owner_id,scope_id,memory_id,kind,
                        current_revision,state,content_sha256,confirmed,sensitivity,retention_policy_ref,index_model_ref,effective_at,expires_at,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,'FACT',1,'ACTIVE',?,true,'PUBLIC',?,?,?,?,?,?)
                    """, entries);
            privileged.batchUpdate("""
                    INSERT INTO tianji_agent.agent_memory_revision(tenant_id,owner_issuer,owner_type,owner_id,scope_id,memory_id,revision,
                        state,content_json,source_json,content_sha256,source_evidence_sha256,source_actor_sha256,confidence,confirmed,sensitivity,
                        retention_policy_ref,index_model_ref,effective_at,expires_at,written_at,reason_code)
                    VALUES (?,?,?,?,?, ?,1,'ACTIVE',?::jsonb,?::jsonb,?,?,?,1,true,'PUBLIC',?,?,?,?,?,'EXPLICIT_CREATE')
                    """, versions);
            privileged.batchUpdate("""
                    INSERT INTO tianji_agent.agent_memory_command_receipt(tenant_id,owner_issuer,owner_type,owner_id,scope_id,actor_type,
                        actor_id,operation,command_id,request_sha256,result_memory_id,result_revision,result_outcome,result_state,created_at)
                    VALUES (?,?,?,?,?, ?,?,'UPSERT',?,?,?,1,'CREATED','ACTIVE',?)
                    """, receipts);
            return tail;
        });
    }

    private static Object[] values(Object[] prefix, Object... tail) {
        Object[] complete = java.util.Arrays.copyOf(prefix, prefix.length + tail.length);
        System.arraycopy(tail, 0, complete, prefix.length, tail.length);
        return complete;
    }

    private void concurrentOwnersAndTail(PostgresMemoryService memory, PostgresMemoryService maintenanceMemory,
                                         PgVectorMemoryIndex maintenanceIndex, JdbcMemoryStore store, MemoryIndexWork index) {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, ALICE.subjectId());
        String scoped = "parallel-cut";
        var scope = scope(ALICE, owner, scoped);
        memory.upsert(context(ALICE), fact("parallel", owner, scoped, "parallel", "locked fact", false));
        var entered = new CountDownLatch(1);
        var complete = new CountDownLatch(1);
        var first = migration(maintenance, store, index, authority((selected, epoch) -> {
            entered.countDown();
            try { assertTrue(complete.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
            memory.upsert(context(ALICE), fact("unrelated-tail", owner, "unrelated-tail", "other", "unrelated pending event", true));
        }));
        var peer = new MemoryIndexWork(maintenance, maintenanceMemory, maintenanceIndex, ignored -> { }, CLOCK);
        var second = migration(maintenance, store, peer, authority((selected, epoch) -> { }));
        try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var winner = tasks.submit(() -> first.freezeAndMigrate(scope, 1, deadline()));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            try {
                assertThrows(AgentLedgerException.class, () -> second.freezeAndMigrate(scope, 1, deadline()));
                assertThrows(AgentLedgerException.class, () -> memory.upsert(context(ALICE), fact("during-freeze", owner, scoped, "late", "late write", false)));
                assertEquals("FROZEN", privileged.queryForObject("SELECT state FROM egon_agent_memory.scope WHERE scope_hash=?",
                        String.class, scope.hash()));
            } finally {
                complete.countDown();
            }
            assertEquals("COMPONENT", winner.get(15, TimeUnit.SECONDS).storageRef());
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private void ambiguousCommittedSwitch(PostgresMemoryService memory, Validator validator) {
        String scoped = "uncertain-commit";
        var scope = scope(ALICE, TEAM, scoped);
        memory.upsert(context(ALICE), fact("uncertain", TEAM, scoped, "initial", "uncertain initial", false));
        var lost = new AtomicBoolean();
        var connectionProxy = new DelegatingDataSource(maintenance) {
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                var delegate = super.getConnection();
                return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{java.sql.Connection.class}, (proxy, method, args) -> {
                    Object result;
                    try { result = method.invoke(delegate, args); }
                    catch (java.lang.reflect.InvocationTargetException original) { throw original.getCause(); }
                    if ("commit".equals(method.getName()) && !lost.get()
                            && "COMPONENT".equals(privileged.queryForObject("SELECT storage_ref FROM egon_agent_memory.scope WHERE scope_hash=?",
                                    String.class, scope.hash()))) {
                        lost.set(true);
                        try (var clients = Executors.newVirtualThreadPerTaskExecutor()) {
                            clients.submit(() -> memory.upsert(context(ALICE),
                                    fact("after-uncertain", TEAM, scoped, "new", "committed by current component", false))).get(5, TimeUnit.SECONDS);
                        }
                        throw new java.sql.SQLException("Commit response lost after PostgreSQL committed");
                    }
                    return result;
                });
            }
        };
        var store = new JdbcMemoryStore(connectionProxy, AF, "COMPONENT", CLOCK, validator);
        var adapter = new LegacyMemoryStoreAdapter(connectionProxy, AF, store, CLOCK);
        try (var vector = new PgVectorMemoryIndex(connectionProxy, binding, embedding, 2, CLOCK)) {
            var selected = new PostgresMemoryService(new AgentLedgerService(connectionProxy), connectionProxy,
                    ignored -> MemoryAuthorization.Decision.ALLOW, List.of(POLICY), publication(connectionProxy, adapter), CLOCK, vector, adapter);
            selected.verifyStorage(2, version);
            var indexes = new MemoryIndexWork(connectionProxy, selected, vector, ignored -> { }, CLOCK);
            var operator = migration(connectionProxy, store, indexes, authority((identity, epoch) -> { }));
            assertEquals(new MemoryScopeMigrationService.CutVO(3, "COMPONENT", 1), operator.freezeAndMigrate(scope, 1, deadline()));
            assertTrue(lost.get());
            assertEquals(2, shadowCount(scope));
            assertEquals(2L, privileged.queryForObject("SELECT high_water_revision FROM egon_agent_memory.scope WHERE scope_hash=?",
                    Long.class, scope.hash()));
        }
    }

    private int shadowCount(MemoryScopeVO scope) {
        return privileged.queryForObject("SELECT count(*) FROM egon_agent_memory.revision WHERE scope_hash=?", Integer.class, scope.hash());
    }

    private void assertLegacyActive(MemoryScopeVO scope, long epoch) {
        assertEquals("LEGACY", privileged.queryForObject("SELECT storage_ref FROM egon_agent_memory.scope WHERE scope_hash=?", String.class, scope.hash()));
        assertEquals("ACTIVE", privileged.queryForObject("SELECT state FROM egon_agent_memory.scope WHERE scope_hash=?", String.class, scope.hash()));
        assertEquals(epoch, privileged.queryForObject("SELECT authority_epoch FROM egon_agent_memory.scope WHERE scope_hash=?", Long.class, scope.hash()));
    }

    private static MemoryScopeVO scope(TrustedAgentActor actor, MemoryOwnerDTO owner, String scoped) {
        return LegacyMemoryStoreAdapter.scopeOf(actor.tenantId(), actor.issuerFingerprint(), owner, scoped);
    }

    private MemoryScopeMigrationService migration(DataSource selected, JdbcMemoryStore store, MemoryIndexWork index,
                                                   MemoryScopeMigrationService.MigrationAuthority gate) {
        return new MemoryScopeMigrationService(selected, AF, store, index, migrationRole, gate, protocol(), CLOCK);
    }

    private static UpsertMemoryCommand fact(String key, MemoryOwnerDTO owner, String scoped, String id, String content, boolean confirmed) {
        return new UpsertMemoryCommand(key, owner, scoped, id, MemoryKindEnum.FACT, 0, new MemoryContentDTO(content, Map.of()),
                PROOF, BigDecimal.ONE, confirmed, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null);
    }

    private MemoryIndexPublication publication() { return publication(source, null); }

    private MemoryIndexPublication publication(DataSource selected, LegacyMemoryStoreAdapter adapter) {
        var jdbc = new JdbcTemplate(selected);
        return new MemoryIndexPublication() {
            @Override public boolean usesDataSource(DataSource candidate) { return selected == candidate; }
            @Override public Binding binding() { return binding; }
            @Override public void publish(Event event) {
                if (adapter != null) adapter.requirePublication(event);
                var protocol = protocol();
                String payload = encode(event);
                var headers = Map.of(HEADER, event.fingerprint());
                jdbc.update("""
                        INSERT INTO tianji_agent.egon_cola_outbox_message(message_id,idempotency_key,message_fingerprint,channel,destination,
                            payload,content_type,schema_version,headers_json,status,max_attempts,next_attempt_at,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,'application/json','1',?,'PENDING',3,clock_timestamp(),clock_timestamp(),clock_timestamp())
                        """, protocol.messageId(event), event.eventId(), protocol.fingerprint(payload, headers), CHANNEL, DESTINATION,
                        payload, LegacyMemoryStoreAdapter.json(headers));
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { events.add(event); }
                });
            }
        };
    }

    private void settleIndexes(MemoryIndexWork index, String targetScope) {
        for (var event : List.copyOf(events)) {
            if (!event.scopeId().equals(targetScope)) continue;
            String state = privileged.queryForObject("SELECT status FROM tianji_agent.egon_cola_outbox_message WHERE idempotency_key=?",
                    String.class, event.eventId());
            if ("SUCCEEDED".equals(state)) continue;
            index.apply(event, deadline());
            assertEquals(1, privileged.update("""
                    UPDATE tianji_agent.egon_cola_outbox_message SET status='SUCCEEDED',completed_at=clock_timestamp()
                    WHERE idempotency_key=? AND status='PENDING'
                    """, event.eventId()));
        }
    }

    private static MemoryScopeMigrationService.MigrationAuthority authority(java.util.function.BiConsumer<MemoryScopeVO, Long> quiesce) {
        return new MemoryScopeMigrationService.MigrationAuthority() {
            @Override public void authorize(MemoryScopeVO scope, Instant deadline) { }
            @Override public MemoryScopeMigrationService.DrainWitnessVO quiesce(MemoryScopeVO scope, long frozenEpoch, Instant deadline) {
                quiesce.accept(scope, frozenEpoch);
                return witnessed(scope, frozenEpoch, deadline);
            }
            @Override public boolean current(MemoryScopeMigrationService.DrainWitnessVO witness) { return true; }
        };
    }

    private static MemoryScopeMigrationService.DrainWitnessVO witnessed(MemoryScopeVO scope, long epoch, Instant deadline) {
        return new MemoryScopeMigrationService.DrainWitnessVO(scope.hash(), epoch, "migration-fixture-generation",
                CLOCK.instant(), deadline, "d".repeat(64), "e".repeat(64));
    }

    private static MemoryScopeMigrationService.OutboxProtocol protocol() {
        return new MemoryScopeMigrationService.OutboxProtocol() {
            @Override public MemoryIndexPublication.Event decode(String payload) {
                try { return EVENT_JSON.readValue(payload, MemoryIndexPublication.Event.class); }
                catch (JsonProcessingException malformed) { throw new IllegalArgumentException("Invalid fixture Outbox payload", malformed); }
            }
            @Override public String messageId(MemoryIndexPublication.Event event) { return event.eventId().substring("memory-index:".length()); }
            @Override public String fingerprint(String payload, Map<String, String> headers) {
                try {
                    var hash = MessageDigest.getInstance("SHA-256");
                    for (String field : List.of(CHANNEL, DESTINATION, payload, "application/json", "1")) frame(hash, field);
                    hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(headers.size()).array());
                    new java.util.TreeMap<>(headers).forEach((key, value) -> { frame(hash, key); frame(hash, value); });
                    return HexFormat.of().formatHex(hash.digest());
                } catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 unavailable", missing); }
            }
        };
    }

    private static String encode(MemoryIndexPublication.Event event) {
        try { return EVENT_JSON.writeValueAsString(event); }
        catch (JsonProcessingException malformed) { throw new IllegalArgumentException("Cannot encode fixture Outbox event", malformed); }
    }

    private static void frame(MessageDigest hash, String field) {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        hash.update(bytes);
    }

    private static Flyway flyway(DataSource source, String target) {
        return Flyway.configure().dataSource(source).schemas(LEGACY).defaultSchema(LEGACY).locations("classpath:db/tianji-agent")
                .target(target).placeholders(Map.of("tianjiMemoryDimensions", "2")).cleanDisabled(true).load();
    }

    private static UpsertMemoryCommand upsert(String key, String id, MemoryContentDTO content) {
        return new UpsertMemoryCommand(key, TEAM, SCOPE, id, MemoryKindEnum.FACT, 0, content, PROOF, BigDecimal.ONE, false,
                MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null);
    }

    private static Instant deadline() { return CLOCK.instant().plusSeconds(25); }
    private static MemoryOperationContext context(TrustedAgentActor actor) { return new MemoryOperationContext(actor, deadline(), null); }
}
