package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.agentflow.api.capability.CallPermitVO;
import top.egon.cola.component.agentflow.api.memory.MemoryFactTypeEnum;
import top.egon.cola.component.agentflow.api.memory.MemoryFactVO;
import top.egon.cola.component.agentflow.api.memory.MemoryStore;
import top.egon.cola.component.agentflow.api.memory.MemoryDeleteCommand;
import top.egon.cola.component.agentflow.api.memory.MemoryQuery;
import top.egon.cola.component.agentflow.api.memory.MemoryWriteCommand;
import top.egon.cola.component.agentflow.api.memory.MemoryScopeVO;
import top.egon.cola.component.agentflow.memory.jdbc.JdbcMemoryStore;
import top.egon.cola.component.agentflow.memory.jdbc.MemoryStoreStorageVerifier;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceWritePermit;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Timeout(120)
class MemoryHostAdapterTest {
    private static final String SCHEMA = "egon_agent_memory";
    private static final TrustedAgentActor ALICE = actor("alice", "https://idp.example/twenty");
    private static final MemoryOwnerDTO TEAM = new MemoryOwnerDTO(MemoryOwnerTypeEnum.TEAM, "team");
    private static final MemoryRetentionPolicy POLICY = new MemoryRetentionPolicy("host-memory", 1, Duration.ofDays(1),
            Duration.ofDays(30), 100, Set.of(MemorySensitivityEnum.CONFIDENTIAL),
            Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);
    private static final MemorySourceDTO PROVENANCE = new MemorySourceDTO("explicit-user-entry", "a".repeat(64), null, null);
    private static final Clock CLOCK = Clock.systemUTC();

    @Test
    void teamScopeRetainsExactUnicodeAndReceiptNamespaceDistinguishesActorAndOperation() throws IOException {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.TEAM, "记".repeat(256));
        var alice = actor("甲".repeat(256), "https://idp.example/twenty");
        var bob = actor("乙".repeat(256), "https://idp.example/twenty");
        var otherIssuer = actor(alice.subjectId(), "https://idp.example/other");
        var scopeId = "域".repeat(128);
        var expected = scope(alice, owner, scopeId);

        assertEquals(expected, LegacyMemoryStoreAdapter.scopeOf(alice.tenantId(), alice.issuerFingerprint(), owner, scopeId));
        assertEquals(expected, LegacyMemoryStoreAdapter.scopeOf(bob.tenantId(), bob.issuerFingerprint(), owner, scopeId));
        assertNotEquals(expected, LegacyMemoryStoreAdapter.scopeOf(otherIssuer.tenantId(), otherIssuer.issuerFingerprint(), owner, scopeId));
        assertNotEquals(expected, LegacyMemoryStoreAdapter.scopeOf(alice.tenantId(), alice.issuerFingerprint(), owner, "other"));
        String upsert = LegacyMemoryStoreAdapter.namespaceOf(alice, "UPSERT");
        assertEquals("HOST_V1:" + encoded("USER", alice.subjectId(), "UPSERT"), upsert);
        assertNotEquals(upsert, LegacyMemoryStoreAdapter.namespaceOf(bob, "UPSERT"));
        assertNotEquals(upsert, LegacyMemoryStoreAdapter.namespaceOf(alice, "FORGET"));
    }

    @Test
    @EnabledIfSystemProperty(named = "tianji.memory.host.postgres.test.url", matches = ".+")
    void actualHostWritersAndReadersUseOneAuthorityAndRollbackTogether() throws Exception {
        String url = System.getProperty("tianji.memory.host.postgres.test.url");
        String role = System.getProperty("tianji.memory.host.postgres.test.runtime-user");
        String version = System.getProperty("tianji.memory.postgres.vector.version");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}"));
        assertNotNull(role);
        assertTrue(role.matches("tianji_it_runtime_[0-9a-f]{12}"));
        assertNotNull(version);
        assertTrue(version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"));

        var admin = new DriverManagerDataSource(url);
        var privileged = new JdbcTemplate(admin);
        assertEquals(0, privileged.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname IN ('tianji_agent','egon_agent_memory')", Integer.class));
        assertEquals(version, privileged.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
        var flyway = Flyway.configure().dataSource(admin).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("18").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                .cleanDisabled(true).load();
        assertEquals(18, flyway.migrate().migrationsExecuted);
        flyway.validate();
        privileged.execute("CREATE SCHEMA egon_agent_memory");
        new ResourceDatabasePopulator(new ClassPathResource("db/egon-agent-memory/V1__create_memory_store.sql")).execute(admin);
        privileged.execute("CREATE TABLE tianji_agent.memory_test_outbox (event_id varchar(128) PRIMARY KEY, fingerprint char(64) NOT NULL)");
        privileged.execute("GRANT USAGE ON SCHEMA tianji_agent,egon_agent_memory TO " + role);
        privileged.execute("GRANT SELECT ON ALL TABLES IN SCHEMA tianji_agent,egon_agent_memory TO " + role);
        privileged.execute("GRANT INSERT,UPDATE ON tianji_agent.agent_memory_scope,tianji_agent.agent_memory_entry,"
                + "tianji_agent.agent_memory_revision,tianji_agent.agent_memory_command_receipt,tianji_agent.agent_memory_index_manifest,"
                + "tianji_agent.agent_memory_vector_v1,tianji_agent.agent_memory_run_grant,tianji_agent.memory_test_outbox TO " + role);
        privileged.execute("GRANT DELETE ON tianji_agent.agent_memory_vector_v1 TO " + role);
        privileged.execute("GRANT INSERT ON tianji_agent.agent_context_snapshot TO " + role);
        privileged.execute("GRANT INSERT ON ALL TABLES IN SCHEMA egon_agent_memory TO " + role);
        privileged.execute("GRANT UPDATE ON egon_agent_memory.scope,egon_agent_memory.fact TO " + role);
        privileged.execute("GRANT UPDATE (state,content,provenance,redacted_at,host_metadata) ON egon_agent_memory.revision TO " + role);

        var source = new DriverManagerDataSource(url);
        source.setUsername(role);
        var jdbc = new JdbcTemplate(source);
        new MemoryStoreStorageVerifier(source, SCHEMA).verify();
        var authority = new AtomicReference<>(MemoryAuthorization.Decision.ALLOW);
        var route = new AtomicReference<LegacyMemoryStoreAdapter>();
        List<MemoryIndexPublication.Event> events = new ArrayList<>();
        var publication = new MemoryIndexPublication() {
            @Override public boolean usesDataSource(DataSource selected) { return selected == source; }
            @Override public Binding binding() { return new Binding("host-memory@1#" + "a".repeat(64), 2, "agent_memory_vector_v1"); }
            @Override public void publish(Event event) {
                route.get().requirePublication(event);
                jdbc.update("INSERT INTO tianji_agent.memory_test_outbox VALUES (?,?)", event.eventId(), event.fingerprint());
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { events.add(event); }
                });
            }
        };
        var index = mock(PgVectorMemoryIndex.class);
        when(index.usesDataSource(source)).thenReturn(true);
        when(index.binding()).thenReturn(publication.binding());
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            var store = new JdbcMemoryStore(source, SCHEMA, "COMPONENT", CLOCK, validator.getValidator());
            var adapter = new LegacyMemoryStoreAdapter(source, SCHEMA, store, CLOCK);
            route.set(adapter);
            var memory = new PostgresMemoryService(new AgentLedgerService(source), source,
                    ignored -> authority.get(), List.of(POLICY), publication, CLOCK, index, adapter);
            memory.verifyStorage(2, version);
            var frozen = scope(ALICE, TEAM, "frozen");
            privileged.update("""
                    INSERT INTO egon_agent_memory.scope VALUES (?,?::jsonb,2,'LEGACY','FROZEN',0,clock_timestamp(),clock_timestamp())
                    """, frozen.hash(), new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(frozen.canonicalScope()).toString());
            var command = command("x".repeat(128), "frozen", "private-fact", "private", true);

            PostgresMemoryAuthorizationTest.assertScopeFailure(() -> memory.upsert(context(ALICE), command));
            assertEquals(0, count(privileged, "tianji_agent.agent_memory_entry"));
            assertEquals(0, count(privileged, "tianji_agent.agent_memory_command_receipt"));
            assertEquals(0, count(privileged, "tianji_agent.memory_test_outbox"));
            assertEquals(0, count(privileged, "egon_agent_memory.fact"));

            privileged.update("UPDATE egon_agent_memory.scope SET state='ACTIVE',authority_epoch=3 WHERE scope_hash=?", frozen.hash());
            var original = memory.upsert(context(ALICE), command);
            assertEquals(1L, original.revision());
            assertEquals(3L, privileged.queryForObject("SELECT authority_epoch FROM tianji_agent.agent_memory_command_receipt", Long.class));
            assertEquals(1L, privileged.queryForObject("SELECT high_water_revision FROM egon_agent_memory.scope WHERE scope_hash=?", Long.class, frozen.hash()));
            assertEquals(1, count(privileged, "tianji_agent.memory_test_outbox"));

            var bob = actor("bob", "https://idp.example/twenty");
            var dedup = memory.upsert(context(bob), command);
            assertEquals(MemoryMutationOutcomeEnum.DEDUPLICATED, dedup.outcome());
            assertEquals(original.memoryId(), dedup.memoryId());
            assertEquals(2, count(privileged, "tianji_agent.agent_memory_command_receipt"));
            var get = new GetMemoryQuery(TEAM, "frozen", "private-fact");
            assertEquals(memory.get(context(ALICE), get), memory.get(context(bob), get));
            PostgresMemoryAuthorizationTest.assertForeignIssuerCannotRead(memory, get, context(actor("bob", "https://idp.example/other")));
            authority.set(MemoryAuthorization.Decision.DENY);
            PostgresMemoryAuthorizationTest.assertActorWithoutPermissionCannotRead(memory, get, context(bob));
            authority.set(MemoryAuthorization.Decision.ALLOW);

            MemoryIndexWorkTest.assertEmbeddingCannotLandInChangedEpoch(privileged, source, memory, index, events.getFirst(), frozen.hash());
            stageLegacySnapshot(privileged, frozen, original.memoryId(), ALICE, bob);
            assertEquals(original, memory.upsert(context(ALICE), command));
            assertEquals(dedup, memory.upsert(context(bob), command));

            var forget = new ForgetMemoryCommand(command.commandId(), TEAM, "frozen", "private-fact", 1, "USER_FORGET");
            privileged.execute("REVOKE UPDATE ON tianji_agent.agent_memory_revision FROM " + role);
            PostgresMemoryAuthorizationTest.assertScopeFailure(() -> memory.forget(context(ALICE), forget));
            privileged.execute("GRANT UPDATE ON tianji_agent.agent_memory_revision TO " + role);
            assertEquals(1, count(privileged, "egon_agent_memory.revision"));
            assertEquals(2, count(privileged, "egon_agent_memory.receipt"));
            assertEquals(1, count(privileged, "tianji_agent.memory_test_outbox"));
            assertEquals("private", memory.get(context(ALICE), get).content().text());
            assertEquals("private", privileged.queryForObject("SELECT content_json->>'text' FROM tianji_agent.agent_memory_revision", String.class));

            insertOrphan(privileged, "frozen", "private-fact");
            PostgresMemoryAuthorizationTest.assertScopeFailure(() -> memory.forget(context(ALICE), forget));
            assertEquals(1, count(privileged, "egon_agent_memory.revision"));
            assertEquals(2, count(privileged, "egon_agent_memory.receipt"));
            assertEquals(1, count(privileged, "tianji_agent.memory_test_outbox"));
            privileged.update("DELETE FROM tianji_agent.agent_memory_vector_v1");

            var forgotten = memory.forget(context(ALICE), forget);
            assertEquals(2L, forgotten.revision());
            assertEquals(forgotten, memory.forget(context(ALICE), forget));
            assertEquals(original, memory.upsert(context(ALICE), command));
            assertEquals(dedup, memory.upsert(context(bob), command));
            assertEquals(MemoryStateEnum.FORGOTTEN, memory.get(context(ALICE), get).state());
            assertEquals(0, privileged.queryForObject("""
                    SELECT count(*) FROM egon_agent_memory.revision WHERE content IS NOT NULL OR host_metadata->'source'<>'null'::jsonb
                    """, Integer.class));
            assertEquals(0, privileged.queryForObject("""
                    SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE content_json IS NOT NULL OR source_json IS NOT NULL
                    """, Integer.class));
            assertEquals(1L, privileged.queryForObject("SELECT current_revision FROM tianji_agent.agent_memory_entry", Long.class));
            assertEquals(3, count(privileged, "egon_agent_memory.receipt"));
            assertEquals(2, count(privileged, "tianji_agent.memory_test_outbox"));

            var evidence = sourceEvidence(admin);
            exerciseNativeHostStore(privileged, memory, adapter, frozen, evidence);
            MemoryRetentionWorkTest.assertOnlyCurrentAuthorityCanExpire(privileged, source, memory, publication, frozen, role);
            exerciseOneShotPreferenceWrite(source, memory, evidence.source());
        }
    }

    private static void stageLegacySnapshot(JdbcTemplate jdbc, MemoryScopeVO scope, String id, TrustedAgentActor... actors) {
        jdbc.update("""
                INSERT INTO egon_agent_memory.revision (
                    scope_hash,fact_id,revision,scope_revision,content_profile,content,provenance,state,confirmed,
                    effective_at,expires_at,redacted_at,host_metadata,native_metadata,content_sha256,created_at
                )
                SELECT ?,memory_id,revision,1,'HOST_V1',content_json,NULL,state,confirmed,effective_at,expires_at,redacted_at,
                    jsonb_build_object('schemaVersion',1,'source',source_json,'sourceEvidenceSha256',source_evidence_sha256,
                        'sourceActorSha256',source_actor_sha256,'confidence',confidence::text,'sensitivity',sensitivity,
                        'retentionPolicyRef',retention_policy_ref,'indexModelRef',index_model_ref,
                        'supersedesRevision',supersedes_revision,'reasonCode',reason_code),
                    NULL,content_sha256,written_at
                FROM tianji_agent.agent_memory_revision WHERE memory_id=?
                """, scope.hash(), id);
        jdbc.update("""
                INSERT INTO egon_agent_memory.fact
                SELECT ?,memory_id,kind,created_at,current_revision,updated_at FROM tianji_agent.agent_memory_entry WHERE memory_id=?
                """, scope.hash(), id);
        for (var actor : actors) {
            jdbc.update("""
                    INSERT INTO egon_agent_memory.receipt
                    SELECT ?,?,command_id,request_sha256,operation,result_outcome,result_state,
                        result_memory_id,result_revision,authority_epoch,created_at
                    FROM tianji_agent.agent_memory_command_receipt WHERE actor_id=? AND result_memory_id=?
                    """, scope.hash(), LegacyMemoryStoreAdapter.namespaceOf(actor, "UPSERT"), actor.subjectId(), id);
        }
        jdbc.update("""
                UPDATE tianji_agent.agent_memory_index_manifest SET index_state='DELETED',deleted_at=clock_timestamp(),
                    updated_at=clock_timestamp() WHERE memory_id=?
                """, id);
        jdbc.update("UPDATE egon_agent_memory.scope SET storage_ref='COMPONENT',state='ACTIVE',authority_epoch=5 WHERE scope_hash=?", scope.hash());
    }

    private static void insertOrphan(JdbcTemplate jdbc, String scope, String id) {
        Map<String, Object> metadata = Map.ofEntries(Map.entry("tenantId", "20"), Map.entry("issuerFingerprint", ALICE.issuerFingerprint()),
                Map.entry("ownerType", TEAM.type().name()), Map.entry("ownerId", TEAM.id()), Map.entry("scopeId", scope), Map.entry("memoryId", id),
                Map.entry("contentRevision","99"), Map.entry("modelRef","unknown"), Map.entry("dimensions","2"), Map.entry("contentSha256","b".repeat(64)),
                Map.entry("effectiveAtMillis",1), Map.entry("expiresAtMillis",2),
                Map.entry("retentionPolicyRef",POLICY.reference()), Map.entry("kind","FACT"), Map.entry("sensitivity","CONFIDENTIAL"));
        jdbc.update("INSERT INTO tianji_agent.agent_memory_vector_v1 VALUES (?,'orphan',?::jsonb,'[1,0]'::public.vector)",
                java.util.UUID.randomUUID(), LegacyMemoryStoreAdapter.json(metadata));
    }

    private static void exerciseNativeHostStore(JdbcTemplate admin, PostgresMemoryService memory, LegacyMemoryStoreAdapter adapter,
                                                MemoryScopeVO scope, LinkedSourceVO evidence) {
        var store = adapter.hostStore(memory, new LegacyMemoryFactConverterImpl());
        Instant effective = CLOCK.instant().minusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        String reference = LegacyMemoryFactConverter.sourceRef(evidence.source());
        var proof = new AtomicReference<>(evidence.source());
        var caller = new AtomicReference<>(ALICE);
        var permitCurrent = new AtomicBoolean(true);
        var revokeOnReadExit = new AtomicBoolean();
        var readPolicies = new AtomicInteger();
        var host = new LegacyMemoryStoreAdapter.HostAuthority() {
            @Override public boolean isCurrent(CallPermitVO permit, String purpose) { return permitCurrent.get(); }
            @Override public MemoryOperationContext context(MemoryScopeVO requested) { return MemoryHostAdapterTest.context(caller.get()); }
            @Override public LegacyMemoryStoreAdapter.VerifiedWriteSourceVO verifyWriteSource(MemoryScopeVO requested, MemoryWriteCommand command) {
                return new LegacyMemoryStoreAdapter.VerifiedWriteSourceVO(proof.get(), BigDecimal.ONE);
            }
            @Override public MemoryStore.WriteMetadataVO authorizeWrite(MemoryScopeVO requested, MemoryWriteCommand command) {
                return new MemoryStore.WriteMetadataVO(true, effective, effective.plusSeconds(3_600), "CONFIDENTIAL", POLICY.reference());
            }
            @Override public MemoryStore.ReadPolicyVO authorizeRead(MemoryScopeVO requested, String purpose) {
                if (revokeOnReadExit.get() && readPolicies.incrementAndGet() == 2) permitCurrent.set(false);
                return new MemoryStore.ReadPolicyVO(Set.of(MemoryFactTypeEnum.FACT),
                        Set.of(new MemoryStore.SourceRefVO(reference, evidence.sequence(), evidence.source().evidenceSha256())),
                        Set.of("CONFIDENTIAL"), Set.of(POLICY.reference()), false, true);
            }
            @Override public void authorizeDelete(MemoryScopeVO requested, MemoryDeleteCommand command) { }
            @Override public MemoryStore.ExtractionPolicyVO authorizeExtraction(MemoryScopeVO requested, String strategy, long version) {
                throw new AssertionError("No extraction in host Memory SPI");
            }
        };
        var permit = new CallPermitVO("host-fixture", MemoryStore.CAPABILITY_REF, scope.hash(), 1, 5L,
                CLOCK.instant().plusSeconds(20), () -> false, host);
        var content = Map.<String, Object>of("text", "native host content", "facts", Map.of());
        var wrongRevision = new MemoryWriteCommand("wrong-revision", 0, "native-wrong-revision", MemoryFactTypeEnum.FACT, content,
                List.of(new MemoryFactVO.ProvenanceVO(reference, evidence.sequence() + 1, evidence.source().evidenceSha256(), evidence.observedAt())));
        assertThrows(CommonException.class, () -> store.upsert(wrongRevision, scope, permit));
        var wrongTime = new MemoryWriteCommand("wrong-time", 0, "native-wrong-time", MemoryFactTypeEnum.FACT, content,
                List.of(new MemoryFactVO.ProvenanceVO(reference, evidence.sequence(), evidence.source().evidenceSha256(), evidence.observedAt().plusSeconds(1))));
        assertThrows(CommonException.class, () -> store.upsert(wrongTime, scope, permit));
        proof.set(PROVENANCE);
        var historical = new MemoryWriteCommand("historical", 0, "native-historical", MemoryFactTypeEnum.FACT, content,
                List.of(new MemoryFactVO.ProvenanceVO(LegacyMemoryFactConverter.sourceRef(PROVENANCE), evidence.sequence(),
                        PROVENANCE.evidenceSha256(), evidence.observedAt())));
        assertThrows(CommonException.class, () -> store.upsert(historical, scope, permit));
        proof.set(evidence.source());
        var command = new MemoryWriteCommand("native-fact", 0, "x".repeat(128), MemoryFactTypeEnum.FACT, content,
                List.of(new MemoryFactVO.ProvenanceVO(reference, evidence.sequence(), evidence.source().evidenceSha256(), evidence.observedAt())));
        var written = store.upsert(command, scope, permit);
        assertEquals(written, store.upsert(command, scope, permit));
        assertEquals("native host content", memory.get(context(ALICE), new GetMemoryQuery(TEAM, "frozen", "native-fact")).content().text());
        caller.set(actor("bob", "https://idp.example/twenty"));
        var read = store.retrieve(new MemoryQuery("host content", 1, Set.of(MemoryFactTypeEnum.FACT)), scope, permit).getItems().getFirst();
        assertEquals("native-fact", read.factId());
        assertEquals(evidence.sequence(), read.provenance().getFirst().sourceRevision());
        assertEquals(evidence.observedAt(), read.provenance().getFirst().observedAt());
        assertTrue(store.retrieve(new MemoryQuery("different content", 1, Set.of(MemoryFactTypeEnum.FACT)), scope, permit).getItems().isEmpty());
        assertTrue(store.retrieve(new MemoryQuery(null, 1, Set.of(MemoryFactTypeEnum.PREFERENCE)), scope, permit).getItems().isEmpty());
        revokeOnReadExit.set(true);
        var denial = assertThrows(CommonException.class, () -> store.retrieve(new MemoryQuery(null, 1, Set.of()), scope, permit));
        assertEquals("PERMIT_REVOKED", denial.getStatus());
        revokeOnReadExit.set(false);
        permitCurrent.set(true);
        admin.update("UPDATE tianji_agent.agent_run SET subject_issuer=? WHERE tenant_id=? AND run_id=?",
                "f".repeat(64), ALICE.tenantId(), evidence.source().sourceRunId());
        try {
            assertTrue(store.retrieve(new MemoryQuery(null, 1, Set.of()), scope, permit).getItems().isEmpty());
        } finally {
            admin.update("UPDATE tianji_agent.agent_run SET subject_issuer=? WHERE tenant_id=? AND run_id=?",
                    ALICE.issuerFingerprint(), ALICE.tenantId(), evidence.source().sourceRunId());
        }
        caller.set(ALICE);
        var delete = new MemoryDeleteCommand("native-fact", 1, "native-delete");
        var deleted = store.delete(delete, scope, permit);
        assertEquals(deleted, store.delete(delete, scope, permit));
        assertEquals(written, store.upsert(command, scope, permit));
        assertTrue(store.retrieve(new MemoryQuery(null, 1, Set.of()), scope, permit).getItems().isEmpty());
    }

    private static LinkedSourceVO sourceEvidence(DataSource source) {
        var ledger = new AgentLedgerService(source);
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofSeconds(5),
                2, 0, 1000, 100, BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
        ledger.publish(ALICE, new AgentDefinitionSpecDTO("host-evidence", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), policy, Map.of()));
        var run = ledger.start(ALICE, new StartAgentRunCommand("host-evidence", 1, null, "host-evidence", null, null,
                new AgentInputDTO("trusted event", null, null), null));
        var claimed = ledger.claim(ALICE, new GetAgentRunQuery(run.runId()), run.revision(), "host-evidence", Duration.ofSeconds(20));
        var finished = ledger.finish(ALICE, claimed.lease(), claimed.run().revision(),
                new AgentExecutionResult(run.runId(), AgentRunStatusEnum.COMPLETED, Map.of(), null, "1", null, null, null));
        var event = ledger.events(ALICE, new AgentEventsQuery(run.runId(), finished.lastEventSequence() - 1, 1)).getFirst();
        var proof = new MemorySourceDTO("verified-host-event", "e".repeat(64), run.runId(), event.eventId());
        return new LinkedSourceVO(proof, event.sequence(), event.occurredAt());
    }

    private static void exerciseOneShotPreferenceWrite(DataSource dataSource, PostgresMemoryService memory, MemorySourceDTO source) {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, ALICE.subjectId());
        var command = new UpsertMemoryCommand("preference-once", owner, "preference-once", "suggestion", MemoryKindEnum.PREFERENCE,
                0, new MemoryContentDTO("suggestion", Map.of()), source, new BigDecimal("0.5"), false,
                MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null);
        Instant deadline = CLOCK.instant().plusSeconds(15);
        var used = new AtomicBoolean();
        var permit = new MemoryPreferenceWritePermit() {
            @Override public TrustedAgentActor actor() { return ALICE; }
            @Override public UpsertMemoryCommand command() { return command; }
            @Override public Instant expiresAt() { return deadline; }
            @Override public boolean usesDataSource(DataSource candidate) { return candidate == dataSource; }
            @Override public void verifyTransaction() {
                assertTrue(TransactionSynchronizationManager.hasResource(dataSource));
                assertTrue(used.compareAndSet(false, true), "a preference lease is fenced once");
            }
        };
        var written = memory.upsert(new MemoryOperationContext(ALICE, deadline, null, permit), command);
        assertTrue(used.get());
        assertEquals(MemoryStateEnum.CANDIDATE, written.stateAtMutation());
    }

    private record LinkedSourceVO(MemorySourceDTO source, long sequence, Instant observedAt) { }

    static MemoryOperationContext context(TrustedAgentActor actor) {
        return new MemoryOperationContext(actor, CLOCK.instant().plusSeconds(20), null);
    }

    static UpsertMemoryCommand command(String key, String scope, String id, String text, boolean confirmed) {
        return new UpsertMemoryCommand(key, TEAM, scope, id, MemoryKindEnum.FACT, 0, new MemoryContentDTO(text, Map.of()),
                PROVENANCE, BigDecimal.ONE, confirmed, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null);
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static TrustedAgentActor actor(String id, String issuer) {
        return TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, id, issuer);
    }

    private static MemoryScopeVO scope(TrustedAgentActor actor, MemoryOwnerDTO owner, String scopeId) throws IOException {
        String tenant = Long.toString(actor.tenantId());
        return new MemoryScopeVO("tianji-memory-v10", tenant, MemoryScopeVO.RetentionScopeEnum.HOST, null, null, null,
                encoded("TIANJI_MEMORY_SCOPE_V10", tenant, actor.issuerFingerprint(), owner.type().name(), owner.id(), scopeId));
    }

    private static String encoded(String... values) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(buffer)) {
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                output.writeInt(bytes.length);
                output.write(bytes);
            }
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
    }
}
