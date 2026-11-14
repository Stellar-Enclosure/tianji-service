package top.egon.cola.platform.tianji.process.server.event;

import org.flywaydb.core.Flyway;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
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
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationBootstrapBehavior;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceConverter;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.GovernedStartPilotProperties;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
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
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftConverter;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftStore;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated("Owns one explicitly provisioned empty Process database")
@Timeout(120)
class AutomationEventAdmissionIT {
    private static final String SCHEMA = "tianji_process";
    private static final String DEFINITION = "orderFlow";
    private static final String ROUTE_KEY = "order-created";
    private static final String EVENT_TYPE = "OrderCreated";
    private static final String SOURCE = "orders-source";
    private static final String OLD_RELEASE = "orders-v1";
    private static final String NEW_RELEASE = "orders-v2";
    private static final String ISSUER = "https://idp.test/main";
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "release-owner", ISSUER);
    private static final ProcessActor PRODUCER = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "orders-producer", ISSUER);
    private static final ProcessActor OTHER = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "orders-producer", "https://idp.test/other");
    private static final ProcessSchemaVO INPUT = objectSchema(Map.of("amount", Map.of("type", "integer")), List.of("amount"));
    private static final ProcessSchemaVO OUTPUT = objectSchema(Map.of(), List.of());
    private static final EventRouteVO ROUTE = new EventRouteVO(ROUTE_KEY, 1, EVENT_TYPE, "START", SOURCE, INPUT,
            List.of(new MappingVO("amount", "event.amount")), Duration.ofSeconds(12), DEFINITION, null, null);

    @Test
    void freezes_the_first_release_and_repairs_committed_native_start_even_after_expiry() throws Exception {
        var database = provision();
        var authority = new FixtureAuthority(database.owner());
        try (var context = context(database.jdbcUrl(), database.runtimeRole(), false)) {
            var fixture = components(context, authority);
            publish(fixture.drafts(), OLD_RELEASE);
            publish(fixture.drafts(), NEW_RELEASE);
            fixture.activation().activate(OWNER, DEFINITION, new ActivationCommand(OLD_RELEASE, 0L, "activate-first"));

            var first = event("event-first", database.now().minusSeconds(60), 17);
            var original = fixture.events().receive(PRODUCER, first);
            assertReceived(original, OLD_RELEASE, 1);
            assertEquals(0, bindingCount(database, original.getReceiptId()));
            assertEquals(0, startCount(database, original.getReceiptId()));
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> fixture.events().observe(OTHER, original.getReceiptId(), instance -> false));

            fixture.activation().activate(OWNER, DEFINITION, new ActivationCommand(NEW_RELEASE, 1L, "activate-second"));
            assertEquals(original, fixture.events().receive(PRODUCER, first));
            var altered = event(first.getEventId(), first.getOccurredAt(), 18);
            var conflict = assertThrows(AutomationOperationException.class, () -> fixture.events().receive(PRODUCER, altered));
            assertEquals(AutomationOperationException.Code.TIANJI_EVENT_CONFLICT, conflict.code());
            String secondCorrelation = "order-" + "x".repeat(249);
            var second = fixture.events().receive(PRODUCER,
                    event("event-second", first.getOccurredAt(), 26).setCorrelationKey(secondCorrelation));
            assertReceived(second, NEW_RELEASE, 2);
            var expiring = fixture.events().receive(PRODUCER, event("event-expires", first.getOccurredAt(), 32));
            assertReceived(expiring, NEW_RELEASE, 2);

            // Finish the native transaction without the independent Inbox publication transaction.
            var committed = fixture.execution().startAdmittedEvent(fixture.inbox(), PRODUCER.tenantId(), original.getReceiptId());
            assertEquals("APPLIED", committed.state());
            assertNotNull(committed.instanceId());
            assertEquals(original, fixture.events().observe(PRODUCER, original.getReceiptId(), instance -> false));
            assertPinned(database, original, committed.instanceId(), first.getCorrelationKey(), first.getCorrelationKey());

            var appliedSecond = fixture.events().advance(PRODUCER.tenantId(), second.getReceiptId());
            assertEquals("APPLIED", appliedSecond.getState());
            String secondLocator = "event-correlation-" + ProcessTypedJson.sha256("AUTOMATION_EVENT_CORRELATION_V2",
                    ProcessTypedJson.canonicalBytes(secondCorrelation));
            assertPinned(database, second, appliedSecond.getInstanceId(), secondCorrelation, secondLocator);

            try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
                var pendingStart = threads.submit(() ->
                        fixture.execution().startAdmittedEvent(fixture.inbox(), PRODUCER.tenantId(), expiring.getReceiptId()));
                try {
                    assertTrue(authority.expiringProofObserved.await(10, TimeUnit.SECONDS));
                    await().atMost(Duration.ofSeconds(20)).until(() -> database.owner().queryForObject("""
                            SELECT count(*)=2 FROM tianji_process.tianji_process_event_inbox
                            WHERE tenant_id=? AND receipt_id IN (?, ?) AND admission_not_after<=clock_timestamp()
                            """, Boolean.class, PRODUCER.tenantId(), original.getReceiptId(), expiring.getReceiptId()));
                    fixture.events().advancePending(20);
                    var rejected = fixture.events().observe(PRODUCER, expiring.getReceiptId(), instance -> false);
                    assertEquals("REJECTED", rejected.getState());
                    assertEquals("EXPIRED", rejected.getDecisionCode());
                } finally {
                    authority.resumeExpiringProof.countDown();
                }
                var late = pendingStart.get(12, TimeUnit.SECONDS);
                assertEquals("REJECTED", late.state());
                assertEquals("EXPIRED", late.decisionCode());
                assertNull(late.instanceId());
            }
            assertEquals(0, bindingCount(database, expiring.getReceiptId()));
            assertEquals(0, startCount(database, expiring.getReceiptId()));
            var repaired = fixture.events().observe(PRODUCER, original.getReceiptId(), instance -> false);
            assertEquals("APPLIED", repaired.getState());
            assertEquals("APPLIED", repaired.getDecisionCode());
            assertEquals(committed.instanceId(), repaired.getInstanceId());
            assertEquals(OLD_RELEASE, repaired.getReleaseVersion());
            assertEquals(1, repaired.getActivationRevision());
            assertEquals(original.getAdmittedAt(), repaired.getAdmittedAt());
            assertEquals(repaired, fixture.events().advance(PRODUCER.tenantId(), original.getReceiptId()));
            assertEquals(repaired, fixture.events().receive(PRODUCER, first));
            assertPinned(database, original, repaired.getInstanceId(), first.getCorrelationKey(), first.getCorrelationKey());
        }
    }

    private static void assertReceived(ProcessEventReceiptDTO receipt, String version, long revision) {
        assertNotNull(receipt.getReceiptId());
        assertEquals("RECEIVED", receipt.getState());
        assertEquals(version, receipt.getReleaseVersion());
        assertEquals(revision, receipt.getActivationRevision());
        assertNotNull(receipt.getAdmittedAt());
        assertEquals(receipt.getAdmittedAt(), receipt.getReceivedAt());
        assertNull(receipt.getInstanceId());
        assertNull(receipt.getDecisionCode());
    }

    private static ProcessEventCommand event(String id, Instant occurredAt, int amount) {
        return new ProcessEventCommand(id, DEFINITION, ROUTE_KEY, ROUTE.version(), EVENT_TYPE, occurredAt,
                "order-" + id, null, Map.of("amount", amount));
    }

    private static void assertPinned(DatabaseVO database, ProcessEventReceiptDTO receipt, String instance, String businessKey,
                                     String correlationLocator) {
        var binding = database.owner().queryForMap("""
                SELECT b.release_key,b.activation_revision,b.admission_kind,b.source_event_receipt_id,b.instance_id,
                       r.state AS start_state,r.resource_id AS start_instance,r.command_id AS start_command,r.correlation_id,
                       n.business_key_,
                       n.proc_def_id_ AS native_definition,d.engine_definition_id AS pinned_definition
                FROM tianji_process.tianji_process_instance_binding b
                JOIN tianji_process.tianji_process_command_receipt r ON r.tenant_id=b.tenant_id
                    AND r.subject_type=b.owner_type AND r.subject_issuer=b.owner_issuer AND r.subject_id=b.owner_id
                    AND r.command_type=b.source_command_type AND r.command_id=b.source_command_id
                JOIN tianji_process.tianji_process_release_definition d ON d.tenant_id=b.tenant_id AND d.release_key=b.release_key
                    AND d.definition_key=b.definition_key AND d.model_type='BPMN'
                JOIN tianji_process.act_ru_execution n ON n.id_=b.instance_id
                WHERE b.tenant_id=? AND b.source_event_receipt_id=?
                """, PRODUCER.tenantId(), receipt.getReceiptId());
        assertEquals(receipt.getReleaseVersion(), binding.get("release_key"));
        assertEquals(receipt.getActivationRevision(), binding.get("activation_revision"));
        assertEquals("EVENT", binding.get("admission_kind"));
        assertEquals(instance, binding.get("instance_id"));
        assertEquals(instance, binding.get("start_instance"));
        assertEquals("APPLIED", binding.get("start_state"));
        assertTrue(binding.get("start_command").toString().matches("evt-v2-[0-9a-f]{64}"));
        assertEquals(correlationLocator, binding.get("correlation_id"));
        assertEquals(businessKey, binding.get("business_key_"));
        assertEquals(binding.get("pinned_definition"), binding.get("native_definition"));
        assertEquals(1, bindingCount(database, receipt.getReceiptId()));
        assertEquals(1, startCount(database, receipt.getReceiptId()));
    }

    private static int bindingCount(DatabaseVO database, String receiptId) {
        return database.owner().queryForObject("""
                SELECT count(*) FROM tianji_process.tianji_process_instance_binding WHERE tenant_id=? AND source_event_receipt_id=?
                """, Integer.class, PRODUCER.tenantId(), receiptId);
    }

    private static int startCount(DatabaseVO database, String receiptId) {
        return database.owner().queryForObject("""
                SELECT count(*) FROM tianji_process.tianji_process_command_receipt c
                JOIN tianji_process.tianji_process_event_inbox e ON e.tenant_id=c.tenant_id
                    AND e.producer_issuer=c.subject_issuer AND e.producer_subject=c.subject_id AND e.command_id=c.command_id
                WHERE e.tenant_id=? AND e.receipt_id=? AND c.command_type='START' AND c.subject_type='SERVICE'
                """, Integer.class, PRODUCER.tenantId(), receiptId);
    }

    private static ProcessSchemaVO objectSchema(Map<String, Object> fields, List<String> required) {
        return ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", fields, "required", required,
                "additionalProperties", false));
    }

    private static void publish(ProcessDraftService drafts, String version) {
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:event-admission">
                  <process id="orderFlow" isExecutable="true">
                    <startEvent id="start"/><endEvent id="end"/>
                    <sequenceFlow id="done" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """;
        var model = new AutomationModelSourceVO("AUTOMATION_V2",
                List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", DEFINITION, xml)),
                INPUT, OUTPUT, List.of(), List.of(ROUTE));
        var draft = drafts.create(OWNER, DEFINITION, new CreateProcessDraftCommand("Orders", null, "draft-" + version));
        var ready = drafts.save(OWNER, draft.getDraftId(), new SaveProcessDraftCommand("save-" + version, 1, "Orders",
                AutomationReleaseStore.copyContent(model)));
        var request = new PublishProcessDraftCommand(ready.getDraftId(), ready.getRevision(), ready.getContentSha256(),
                version, "publish-" + version);
        assertEquals(version, drafts.publish(OWNER, request, Instant.now().plusSeconds(60)).releaseVersion());
    }

    private static ComponentsVO components(ConfigurableApplicationContext context, FixtureAuthority fixture) {
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
        var environment = new MockEnvironment().withProperty("stellar.tianji.process.automation.enabled", "true")
                .withProperty("stellar.tianji.process.events.enabled", "true");
        var releases = new ProcessReleaseService(process, dmn, new ProcessModelValidator());
        var sealed = new AutomationReleaseStore(jdbc, SCHEMA);
        var verifier = new ProcessApiSchemaVerifier(source, SCHEMA);
        verifier.verifyEventsV15();
        var activations = new AutomationActivationStore(jdbc, SCHEMA);
        var authorities = new StaticListableBeanFactory(Map.of("admission", (AutomationAdmissionAuthority) fixture::proof,
                "trust", (AutomationReleaseTrustVerifier.TrustAuthority) fixture::signed));
        var admission = authorities.getBeanProvider(AutomationAdmissionAuthority.class);
        var trust = authorities.getBeanProvider(AutomationReleaseTrustVerifier.TrustAuthority.class);
        fixture.releases = sealed;
        var registeredRoute = new AutomationModelValidator.EventRegistrationVO(ROUTE, "a".repeat(64));
        var routeRegistrations = Map.of(new AutomationModelValidator.VersionedRefVO(ROUTE_KEY, ROUTE.version()), registeredRoute);
        var dependencyResolver = new AutomationReleaseStore.DependencyResolver() {
            @Override
            public AutomationModelValidator.DependencySnapshotVO resolve(ProcessActor actor, AutomationModelSourceVO model) {
                return new AutomationModelValidator.DependencySnapshotVO(actor.tenantId(), Map.of(), Map.of(), Set.of(),
                        Map.of(), routeRegistrations);
            }

            @Override
            public boolean isCurrent(ProcessActor actor, AutomationModelSourceVO model,
                                     AutomationModelValidator.DependencySnapshotVO observed) {
                return resolve(actor, model).equals(observed);
            }
        };
        var drafts = new ProcessDraftService(new ProcessDraftStore(jdbc, SCHEMA), new ProcessDraftCommandReceiptStore(jdbc, SCHEMA),
                new ProcessDraftContentCodec(), Mappers.getMapper(ProcessDraftConverter.class), verifier, transaction, environment,
                releases, sealed, new AutomationModelValidator(), new AutomationModelCompiler(), dependencyResolver);
        var activation = new AutomationActivationService(activations, sealed, releases, Mappers.getMapper(AutomationActivationConverter.class),
                verifier, transaction, environment, admission, trust);
        var dependencies = new ProcessExecutionService.AutomationDependenciesVO(activations, sealed,
                new ProcessInstanceBindingStore(jdbc, SCHEMA), Mappers.getMapper(AutomationInstanceConverter.class),
                verifier, environment, admission, trust);
        var execution = new ProcessExecutionService(process, dmn, new ProcessModelValidator(),
                new GovernedStartPilotProperties(List.of()), null, dependencies);
        var inbox = new ProcessEventInboxStore(jdbc, SCHEMA);
        var events = new ProcessEventApplicationService(inbox, execution, transaction, environment,
                (producer, definition, release, routeKey, version, routeSha256) -> PRODUCER.equals(producer)
                        && DEFINITION.equals(definition) && Set.of(OLD_RELEASE, NEW_RELEASE).contains(release)
                        && ROUTE_KEY.equals(routeKey) && version == ROUTE.version() && "a".repeat(64).equals(routeSha256));
        return new ComponentsVO(drafts, activation, execution, inbox, events);
    }

    private record ComponentsVO(ProcessDraftService drafts, AutomationActivationService activation, ProcessExecutionService execution,
                                ProcessEventInboxStore inbox, ProcessEventApplicationService events) { }

    private record DatabaseVO(JdbcTemplate owner, String jdbcUrl, String runtimeRole) {
        Instant now() {
            return owner.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
        }
    }

    private static final class FixtureAuthority {
        private final JdbcTemplate owner;
        private final KeyPair key;
        private final Instant issued;
        private final AtomicBoolean interceptExpiry = new AtomicBoolean(true);
        private final CountDownLatch expiringProofObserved = new CountDownLatch(1);
        private final CountDownLatch resumeExpiringProof = new CountDownLatch(1);
        private AutomationReleaseStore releases;

        private FixtureAuthority(JdbcTemplate owner) throws GeneralSecurityException {
            this.owner = owner;
            this.key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            this.issued = now().minusSeconds(2);
        }

        private Instant now() {
            return owner.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
        }

        private AutomationAdmissionProofVO proof(AutomationAdmissionQuery query) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            var purpose = query.getPurpose();
            if (purpose == AutomationAdmissionQuery.Purpose.EVENT_ADMIT || purpose == AutomationAdmissionQuery.Purpose.EVENT_START) {
                assertEquals(PRODUCER, query.getActor());
                var event = query.getEventIdentity();
                assertEquals(PRODUCER.issuerFingerprint(), event.producerIssuer());
                assertEquals(PRODUCER.subjectId(), event.producerSubject());
                assertEquals(query.getRequestSha256(), event.eventFingerprint());
                assertNotNull(query.getRouteSha256());
                assertEquals(purpose == AutomationAdmissionQuery.Purpose.EVENT_START, query.getAdmissionReceiptId() != null);
            }
            var evidence = signed(query, releases.find(query.getActor().tenantId(), query.getReleaseVersion()).orElseThrow());
            Instant now = now();
            if (purpose == AutomationAdmissionQuery.Purpose.EVENT_START
                    && "event-expires".equals(query.getEventIdentity().eventId()) && interceptExpiry.compareAndSet(true, false)) {
                expiringProofObserved.countDown();
                try {
                    assertTrue(resumeExpiringProof.await(23, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return new AutomationAdmissionProofVO(query.sha256(), "fixture-permit", "a".repeat(64), now, now.plusSeconds(25),
                    evidence.currentGeneration(), evidence.envelopeSha256(), evidence.payloadSha256(),
                    evidence.dependencyEvidenceSha256(query), Map.of());
        }

        private AutomationReleaseTrustVerifier.TrustObservationVO signed(AutomationAdmissionQuery query,
                AutomationReleaseStore.ImmutableReleaseVO release) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(query.getReleaseVersion(), release.releaseVersion());
            try {
                byte[] encoded = key.getPublic().getEncoded();
                String keyId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
                Instant expires = issued.plusSeconds(180);
                var trusted = new AutomationReleaseTrustVerifier.TrustedKeyVO(keyId, release.tenantId(), 1,
                        Base64.getEncoder().encodeToString(encoded), issued.minusSeconds(1), expires);
                var signature = Signature.getInstance("Ed25519");
                signature.initSign(key.getPrivate());
                signature.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, issued, expires));
                var envelope = new AutomationReleaseTrustVerifier.SignedEnvelopeVO(keyId, issued, expires,
                        Base64.getEncoder().encodeToString(signature.sign()));
                return new AutomationReleaseTrustVerifier.TrustObservationVO(trusted, 1, envelope, release.dependencies(),
                        AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release)));
            } catch (GeneralSecurityException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static DatabaseVO provision() {
        String url = System.getProperty("tianji.process.postgres.test.url", "");
        String role = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !role.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Event admission requires an empty loopback database with a dedicated role");
        }
        var ownerSource = new DriverManagerDataSource(url);
        var settings = new Properties();
        settings.setProperty("connectTimeout", "5");
        settings.setProperty("socketTimeout", "20");
        ownerSource.setConnectionProperties(settings);
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
                .locations("classpath:db/tianji-process").target("15").cleanDisabled(true).load();
        assertEquals(15, flyway.migrate().migrationsExecuted);
        flyway.validate();

        String scoped = url + "?currentSchema=" + SCHEMA;
        try (var installing = context(scoped, System.getProperty("user.name"), true)) {
            assertNotNull(installing.getBean(RepositoryService.class));
        }
        String quoted = "\"" + role + "\"";
        owner.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quoted);
        grant(owner, quoted, "SELECT", "tianji_process_schema_history", "tianji_platform_release_activation");
        grant(owner, quoted, "SELECT, INSERT, UPDATE", "tianji_process_release", "tianji_process_release_definition",
                "tianji_process_command_receipt", "tianji_process_draft", "tianji_process_node_execution",
                "tianji_process_event_inbox", "tianji_process_event_subscription");
        grant(owner, quoted, "SELECT, INSERT", "tianji_governed_process_start", "tianji_process_automation_release",
                "tianji_process_draft_command", "tianji_process_automation_activation", "tianji_process_instance_binding");
        owner.execute("GRANT UPDATE (created_at) ON " + SCHEMA + ".tianji_process_instance_binding TO " + quoted);
        var nativeTables = owner.queryForList("""
                SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname=? AND tablename LIKE 'act#_%' ESCAPE '#'
                """, String.class, SCHEMA);
        assertTrue(nativeTables.contains("act_re_deployment"));
        for (String table : nativeTables) {
            assertTrue(table.matches("act_[a-z0-9_]+"));
            grant(owner, quoted, "SELECT, INSERT, UPDATE, DELETE", table);
        }
        return new DatabaseVO(owner, scoped, role);
    }

    private static void grant(JdbcTemplate owner, String role, String privilege, String... names) {
        for (String name : names) owner.execute("GRANT " + privilege + " ON " + SCHEMA + "." + name + " TO " + role);
    }

    private static ConfigurableApplicationContext context(String url, String user, boolean install) {
        return new SpringApplicationBuilder(NativeTestConfiguration.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user, "--spring.main.banner-mode=off",
                "--flowable.database-schema-update=" + install, "--egon.cola.component.transactional-outbox.enabled=false",
                "--flowable.async-executor-activate=false");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class NativeTestConfiguration {
        @Bean
        EngineConfigurationConfigurer<SpringProcessEngineConfiguration> bootstrap() {
            return config -> {
                var source = ((DataSourceTransactionManager) config.getTransactionManager()).getDataSource();
                var jdbc = new JdbcTemplate(source);
                var bindings = new ProcessInstanceBindingStore(jdbc, SCHEMA);
                var releases = new AutomationReleaseStore(jdbc, SCHEMA);
                config.setCustomPostCommandInterceptors(List.of(new AutomationInstanceGuardCommandInterceptor(jdbc, SCHEMA, bindings)));
                AutomationInstanceGuardCommandInterceptor.registerFailedJobHandling(config);
                AutomationBootstrapBehavior.register(config, () -> new AutomationBootstrapBehavior(bindings, releases));
            };
        }
    }
}
