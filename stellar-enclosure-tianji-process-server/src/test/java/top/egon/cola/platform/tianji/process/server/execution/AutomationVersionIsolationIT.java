package top.egon.cola.platform.tianji.process.server.execution;

import org.flywaydb.core.Flyway;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ListProcessInstancesByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationConverter;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationService;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionProofVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftConverter;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Isolated("Owns one explicitly provisioned empty Process database")
@Timeout(120)
class AutomationVersionIsolationIT {
    private static final String SCHEMA = "tianji_process";
    private static final String ENABLED = "stellar.tianji.process.automation.enabled";
    private static final String DEFINITION = "review";
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "starter", "https://idp.test/main");
    private static final ProcessActor DIFFERENT_ISSUER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "starter", "https://idp.test/other");
    private static final ProcessActor OUTSIDE = ProcessActor.verified(21, ProcessActor.SubjectType.USER, "starter", "https://idp.test/main");
    private static final String XML = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:step25">
              <process id="review" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="done" sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;
    private static final String INPUT = """
            {"type":"object","properties":{"amount":{"type":"string","format":"decimal:2"}},
            "required":["amount"],"additionalProperties":false}
            """;
    private static final String EMPTY = """
            {"type":"object","properties":{},"required":[],"additionalProperties":false}
            """;

    @Test
    void originalAppliedStartUsesRawDigestAfterCutoverAndRollsBackWithNativeJob() throws Exception {
        var database = provision();
        var environment = new MockEnvironment().withProperty(ENABLED, "true");
        var authority = new FixtureAuthority(database.owner());
        try (var first = context(database.url(), database.role(), false); var reopened = context(database.url(), database.role(), false)) {
            var running = components(first, environment, authority);
            var again = components(reopened, environment, authority);
            publish(running.drafts(), "review-v1");
            publish(running.drafts(), "review-v2");
            running.activation().activate(OWNER, DEFINITION, new ActivationCommand("review-v1", 0L, "activate-first"));
            var original = start("start-original", "1.0");
            assertEquals(0, rowCount(database, "tianji_process_instance_binding"));
            var started = running.execution().startAutomation(OWNER, original);
            assertEquals("review-v1", started.getReleaseVersion());
            assertEquals(1, started.getActivationRevision());
            assertEquals(1, rowCount(database, "tianji_process_instance_binding"));
            assertEquals(1, rowCount(database, "act_ru_job"));
            assertNativePinned(database, started, original);
            assertThrows(ProcessResourceNotFoundException.class, () -> running.execution().get(OWNER, started.getInstanceId()));
            assertThrows(ProcessConflictException.class, () -> running.execution().start(OWNER, new ProcessExecutionService.StartCommand(
                    "review-v1", DEFINITION, original.getBusinessKey(), original.getCommandId(), original.getCorrelationId(),
                    original.getVariables())));
            Instant to = database.owner().queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
            var correlation = new ListProcessInstancesByCorrelationQuery(original.getCorrelationId(), to.minusSeconds(30), to, null);
            assertTrue(running.execution().listByCorrelation(OWNER, correlation).items().isEmpty());

            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                var competing = pool.submit(() -> running.execution().startAutomation(OWNER, start("start-race", "2.00")));
                try {
                    assertTrue(authority.startObserved.await(12, TimeUnit.SECONDS));
                    again.activation().activate(OWNER, DEFINITION, new ActivationCommand("review-v2", 1L, "activate-second"));
                } finally {
                    authority.completeObservation.countDown();
                }
                var conflict = assertThrows(ExecutionException.class, () -> competing.get(12, TimeUnit.SECONDS));
                var reason = assertInstanceOf(AutomationOperationException.class, conflict.getCause());
                assertEquals(AutomationOperationException.Code.TIANJI_PROCESS_ACTIVATION_CONFLICT, reason.code());
            }
            assertEquals(1, rowCount(database, "tianji_process_instance_binding"));
            assertEquals(1, rowCount(database, "act_ru_job"));
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> again.execution().getStartReceipt(OWNER, lookup(start("start-race", "2.00"))));

            var injected = assertThrows(IllegalStateException.class,
                    () -> running.execution().startAutomation(OWNER, start("start-rollback", "9")));
            assertEquals("Fixture failed after native START", injected.getMessage());
            assertEquals(1, rowCount(database, "tianji_process_instance_binding"));
            assertEquals(1, rowCount(database, "act_ru_job"));
            assertEquals(1, rowCount(database, "act_hi_procinst"));
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> running.execution().getStartReceipt(OWNER, lookup(start("start-rollback", "9"))));

            var second = again.execution().startAutomation(OWNER, start("start-second", "2.00"));
            assertEquals("review-v2", second.getReleaseVersion());
            assertEquals(2, second.getActivationRevision());
            assertEquals(started.getInstanceId(), again.execution().getStartReceipt(OWNER, lookup(original)).instanceId());
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> again.execution().getStartReceipt(OWNER, lookup(start("start-original", "1.00"))));
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> again.execution().getStartReceipt(DIFFERENT_ISSUER, lookup(original)));
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> again.execution().getStartReceipt(OUTSIDE, lookup(original)));

            environment.setProperty(ENABLED, "false");
            authority.liveTrust.set(false);
            var replay = again.execution().startAutomation(OWNER, original);
            assertEquals(started.getInstanceId(), replay.getInstanceId());
            assertEquals(1, replay.getActivationRevision());
            assertThrows(ProcessConflictException.class,
                    () -> again.execution().startAutomation(OWNER, start("start-original", "1.00")));
            assertEquals(started.getInstanceId(), again.execution().getStartReceipt(OWNER, lookup(original)).instanceId());
            assertEquals("review-v1", again.execution().observeAutomation(OWNER, started.getInstanceId(),
                    new ProcessExecutionService.AutomationObservationPermitVO(true, false, false, false, true)).getReleaseVersion());
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> again.execution().observeAutomation(DIFFERENT_ISSUER, started.getInstanceId(),
                            new ProcessExecutionService.AutomationObservationPermitVO(true, false, false, false, false)));
            assertEquals(started.getInstanceId(), again.execution().observeAutomation(DIFFERENT_ISSUER, started.getInstanceId(),
                    new ProcessExecutionService.AutomationObservationPermitVO(false, true, false, false, false)).getInstanceId());
            assertEquals(2, rowCount(database, "tianji_process_instance_binding"));

            var process = reopened.getBean(SpringProcessEngineConfiguration.class);
            var job = process.getManagementService().createJobQuery().processInstanceId(started.getInstanceId()).singleResult();
            assertNotNull(job);
            process.getManagementService().executeJob(job.getId());
            var completed = again.execution().observeAutomation(OWNER, started.getInstanceId(),
                    new ProcessExecutionService.AutomationObservationPermitVO(true, false, false, false, false));
            assertEquals(AutomationInstanceDTO.StateEnum.COMPLETED, completed.getState());
            assertNull(completed.getOutput());
            assertEquals(started.getInstanceId(), again.execution().getStartReceipt(OWNER, lookup(original)).instanceId());

            assertEquals(1, database.owner().update("""
                    UPDATE tianji_process.tianji_process_instance_binding SET resolved_start_sha256=?
                    WHERE tenant_id=? AND instance_id=?
                    """, "d".repeat(64), OWNER.tenantId(), started.getInstanceId()));
            assertThrows(IllegalStateException.class, () -> again.execution().getStartReceipt(OWNER, lookup(original)));
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> again.execution().getStartReceipt(OWNER, lookup(start("start-original", "1.00"))));
            assertEquals(1, database.owner().update("""
                    DELETE FROM tianji_process.tianji_process_instance_binding WHERE tenant_id=? AND instance_id=?
                    """, OWNER.tenantId(), started.getInstanceId()));
            assertThrows(IllegalStateException.class, () -> again.execution().getStartReceipt(OWNER, lookup(original)));
        }
    }

    private static AutomationStartCommand start(String commandId, String amount) {
        return new AutomationStartCommand(DEFINITION, "order-42", commandId, "correlation-42", Map.of("amount", amount), Map.of());
    }

    private static GetProcessStartReceiptQuery lookup(AutomationStartCommand command) {
        return new GetProcessStartReceiptQuery(command.getCommandId(), command.requestSha256());
    }

    private static void assertNativePinned(DatabaseVO db, AutomationInstanceDTO instance, AutomationStartCommand original) {
        var evidence = db.owner().queryForMap("""
                SELECT r.proc_def_id_ AS native_definition, b.engine_definition_id AS pinned_definition, i.start_request_sha256 AS raw,
                  i.resolved_start_sha256 AS resolved, c.request_sha256 AS receipt
                FROM tianji_process.tianji_process_instance_binding i
                JOIN tianji_process.tianji_process_command_receipt c ON c.tenant_id=i.tenant_id
                   AND c.subject_type=i.owner_type AND c.subject_issuer=i.owner_issuer AND c.subject_id=i.owner_id
                   AND c.command_type=i.source_command_type AND c.command_id=i.source_command_id
                JOIN tianji_process.tianji_process_release_definition b ON b.tenant_id=i.tenant_id AND b.release_key=i.release_key
                   AND b.definition_key=i.definition_key AND b.model_type='BPMN'
                JOIN tianji_process.act_ru_execution r ON r.id_=i.instance_id
                WHERE i.tenant_id=? AND i.instance_id=? AND c.state='APPLIED' AND c.resource_id=i.instance_id
                """, OWNER.tenantId(), instance.getInstanceId());
        assertEquals(evidence.get("native_definition"), evidence.get("pinned_definition"));
        assertEquals(original.requestSha256(), evidence.get("raw"));
        assertEquals(evidence.get("resolved"), evidence.get("receipt"));
        assertNotEquals(evidence.get("raw"), evidence.get("resolved"));
    }

    private static void publish(ProcessDraftService drafts, String version) {
        var draft = drafts.create(OWNER, DEFINITION, new CreateProcessDraftCommand("Review", null, "draft-" + version));
        var content = new DraftContentVO(List.of(new DraftModelSourceVO("BPMN", DEFINITION, XML)), INPUT, EMPTY, "[]", "[]");
        var ready = drafts.save(OWNER, draft.getDraftId(), new SaveProcessDraftCommand("save-" + version, 1, "Review", content));
        var request = new PublishProcessDraftCommand(ready.getDraftId(), ready.getRevision(), ready.getContentSha256(), version, "publish-" + version);
        assertEquals(version, drafts.publish(OWNER, request, Instant.now().plusSeconds(60)).releaseVersion());
    }

    private static ComponentsVO components(ConfigurableApplicationContext context, MockEnvironment environment, FixtureAuthority fixture) {
        var process = context.getBean(SpringProcessEngineConfiguration.class);
        var dmn = context.getBean(SpringDmnEngineConfiguration.class);
        process.setBeans(Map.of());
        dmn.setBeans(Map.of());
        var manager = (DataSourceTransactionManager) process.getTransactionManager();
        var source = manager.getDataSource();
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(10);
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var releases = new ProcessReleaseService(process, dmn, new ProcessModelValidator());
        var sealed = new AutomationReleaseStore(jdbc, SCHEMA);
        var ver = new ProcessApiSchemaVerifier(source, SCHEMA);
        var activations = new AutomationActivationStore(jdbc, SCHEMA);
        var provider = new StaticListableBeanFactory(Map.of("admission", (AutomationAdmissionAuthority) fixture::proof,
                "trust", (AutomationReleaseTrustVerifier.TrustAuthority) fixture::signed));
        var permission = provider.getBeanProvider(AutomationAdmissionAuthority.class);
        var trust = provider.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class);
        var drafts = new ProcessDraftService(new ProcessDraftStore(jdbc, SCHEMA), new ProcessDraftCommandReceiptStore(jdbc, SCHEMA),
                new ProcessDraftContentCodec(), Mappers.getMapper(ProcessDraftConverter.class), ver, transaction, environment, releases,
                sealed, new AutomationModelValidator(), new AutomationModelCompiler(),
                (actor, model) -> AutomationModelValidator.DependencySnapshotVO.empty(actor.tenantId()));
        var activation = new AutomationActivationService(activations, sealed, releases, Mappers.getMapper(AutomationActivationConverter.class),
                ver, transaction, environment, permission, trust);
        var bindingStore = new ProcessInstanceBindingStore(jdbc, SCHEMA) {
            @Override
            public void insert(AutomationInstanceBindingBO binding) {
                if ("start-rollback".equals(binding.getSourceCommandId())) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(1, jdbc.queryForObject("""
                            SELECT count(*) FROM tianji_process.act_ru_job WHERE process_instance_id_=?
                            """, Integer.class, binding.getInstanceId()));
                    assertEquals(1, jdbc.queryForObject("""
                            SELECT count(*) FROM tianji_process.tianji_process_command_receipt
                            WHERE tenant_id=? AND command_id=? AND command_type='START'
                            """, Integer.class, OWNER.tenantId(), binding.getSourceCommandId()));
                    throw new IllegalStateException("Fixture failed after native START");
                }
                super.insert(binding);
            }
        };
        var dependencies = new ProcessExecutionService.AutomationDependenciesVO(activations, sealed, bindingStore,
                Mappers.getMapper(AutomationInstanceConverter.class), ver, environment, permission, trust);
        var execution = new ProcessExecutionService(process, dmn, new ProcessModelValidator(), new GovernedStartPilotProperties(List.of()), null, dependencies);
        fixture.releases = sealed;
        return new ComponentsVO(drafts, activation, execution);
    }

    private static int rowCount(DatabaseVO database, String table) {
        return database.owner().queryForObject("SELECT count(*) FROM " + SCHEMA + "." + table, Integer.class);
    }

    private record ComponentsVO(ProcessDraftService drafts, AutomationActivationService activation, ProcessExecutionService execution) { }
    private record DatabaseVO(JdbcTemplate owner, String url, String role) { }

    private static final class FixtureAuthority {
        private final JdbcTemplate observer;
        private final java.security.KeyPair key;
        private final Instant keyIssued = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(30);
        private final AtomicBoolean liveTrust = new AtomicBoolean(true);
        private final CountDownLatch startObserved = new CountDownLatch(1);
        private final CountDownLatch completeObservation = new CountDownLatch(1);
        private AutomationReleaseStore releases;

        private FixtureAuthority(JdbcTemplate observer) throws Exception {
            this.observer = observer;
            key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        }

        private AutomationAdmissionProofVO proof(AutomationAdmissionQuery query) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            Instant now = observer.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
            if (query.getPurpose() == AutomationAdmissionQuery.Purpose.REPLAY_START) {
                return new AutomationAdmissionProofVO(query.sha256(), "replay-permit", "a".repeat(64), now, now.plusSeconds(25),
                        1L, "b".repeat(64), "c".repeat(64), "d".repeat(64), Map.of());
            }
            if ("start-race".equals(query.getCommandId())) {
                startObserved.countDown();
                await(completeObservation);
            }
            var material = signed(query, releases.find(query.getActor().tenantId(), query.getReleaseVersion()).orElseThrow());
            return new AutomationAdmissionProofVO(query.sha256(), "start-permit", "a".repeat(64), now, now.plusSeconds(25),
                    material.currentGeneration(), material.envelopeSha256(), material.payloadSha256(),
                    material.dependencyEvidenceSha256(query), Map.of());
        }

        private AutomationReleaseTrustVerifier.TrustObservationVO signed(
                AutomationAdmissionQuery query, AutomationReleaseStore.ImmutableReleaseVO release) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(liveTrust.get(), "Historical replay must not use live trust");
            try {
                byte[] encoded = key.getPublic().getEncoded();
                String keyId = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(encoded));
                Instant expires = keyIssued.plusSeconds(300);
                var trusted = new AutomationReleaseTrustVerifier.TrustedKeyVO(keyId, release.tenantId(), 1,
                        Base64.getEncoder().encodeToString(encoded), keyIssued.minusSeconds(1), expires);
                var signer = Signature.getInstance("Ed25519");
                signer.initSign(key.getPrivate());
                signer.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, keyIssued, expires));
                var envelope = new AutomationReleaseTrustVerifier.SignedEnvelopeVO(keyId, keyIssued, expires,
                        Base64.getEncoder().encodeToString(signer.sign()));
                return new AutomationReleaseTrustVerifier.TrustObservationVO(trusted, 1, envelope, release.dependencies(),
                        AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release)));
            } catch (java.security.GeneralSecurityException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(12, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static DatabaseVO provision() {
        String url = System.getProperty("tianji.process.postgres.test.url", "");
        String role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !role.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Automation START requires an empty loopback database with a dedicated role");
        }
        var ownerSource = new DriverManagerDataSource(url);
        var properties = new Properties();
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "20");
        ownerSource.setConnectionProperties(properties);
        var owner = new JdbcTemplate(ownerSource);
        owner.setQueryTimeout(10);
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT current_database() LIKE 'tianji_it_%' AND NOT pg_is_in_recovery()
                   AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'
                """, Boolean.class)));
        assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA));
        assertTrue(Boolean.TRUE.equals(owner.queryForObject("""
                SELECT rolcanlogin AND NOT rolsuper AND NOT rolcreaterole AND NOT rolcreatedb
                    AND NOT rolreplication AND NOT rolbypassrls
                    AND NOT has_database_privilege(rolname,current_database(),'CREATE')
                    AND NOT has_schema_privilege(rolname,'public','CREATE')
                FROM pg_roles WHERE rolname=?
                """, Boolean.class, role)));
        assertEquals(0, owner.queryForObject("""
                SELECT count(*) FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname=?)
                """, Integer.class, role));
        owner.execute("CREATE SCHEMA " + SCHEMA);
        var flyway = Flyway.configure().dataSource(ownerSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").createSchemas(false).baselineOnMigrate(false)
                .locations("classpath:db/tianji-process").target("14").cleanDisabled(true).load();
        assertEquals(14, flyway.migrate().migrationsExecuted);
        flyway.validate();
        String scoped = url + "?currentSchema=" + SCHEMA;
        try (var installing = context(scoped, System.getProperty("user.name"), true)) {
            assertNotNull(installing.getBean(org.flowable.engine.RepositoryService.class));
        }
        String quoted = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quoted);
        grant(owner, quoted, "SELECT", "tianji_process_schema_history", "tianji_platform_release_activation");
        grant(owner, quoted, "SELECT,INSERT,UPDATE", "tianji_process_release", "tianji_process_release_definition",
                "tianji_process_command_receipt", "tianji_process_draft", "tianji_process_node_execution");
        grant(owner, quoted, "SELECT,INSERT", "tianji_governed_process_start", "tianji_process_automation_release",
                "tianji_process_draft_command", "tianji_process_automation_activation", "tianji_process_instance_binding");
        owner.execute("GRANT UPDATE (created_at) ON " + SCHEMA + ".tianji_process_instance_binding TO " + quoted);
        var nativeTables = owner.queryForList("""
                SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname=? AND tablename LIKE 'act#_%' ESCAPE '#'
                """, String.class, SCHEMA);
        assertTrue(nativeTables.contains("act_re_deployment"));
        for (String table : nativeTables) {
            assertTrue(table.matches("act_[a-z0-9_]+"));
            grant(owner, quoted, "SELECT,INSERT,UPDATE,DELETE", table);
        }
        return new DatabaseVO(owner, scoped, role);
    }

    private static void grant(JdbcTemplate jdbc, String role, String privilege, String... names) {
        for (String name : names) jdbc.execute("GRANT " + privilege + " ON " + SCHEMA + "." + name + " TO " + role);
    }

    private static ConfigurableApplicationContext context(String url, String user, boolean install) {
        return new SpringApplicationBuilder(NativeTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user, "--spring.main.banner-mode=off",
                "--flowable.database-schema-update=" + install, "--egon.cola.component.transactional-outbox.enabled=false");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class NativeTestConfiguration {
        @Bean
        org.flowable.spring.boot.EngineConfigurationConfigurer<SpringProcessEngineConfiguration> bootstrap() {
            return config -> {
                var source = ((DataSourceTransactionManager) config.getTransactionManager()).getDataSource();
                var jdbc = new JdbcTemplate(source);
                var bindings = new ProcessInstanceBindingStore(jdbc, SCHEMA);
                config.setCustomPostCommandInterceptors(List.of(new AutomationInstanceGuardCommandInterceptor(jdbc, SCHEMA, bindings)));
                AutomationInstanceGuardCommandInterceptor.registerFailedJobHandling(config);
                AutomationBootstrapBehavior.register(config, () -> new AutomationBootstrapBehavior(bindings, new AutomationReleaseStore(jdbc, SCHEMA)));
            };
        }
    }
}
