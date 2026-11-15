package top.egon.cola.platform.tianji.process.server.acceptance;

import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO.TemplateRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO.MappingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.process.server.TianjiProcessServerApplication;
import top.egon.cola.platform.tianji.process.server.bridge.ProcessAgentBridgeConfiguration.TypedRouteProvider;
import top.egon.cola.platform.tianji.process.server.event.ProcessEventApplicationService.EventSourceAuthorizer;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.AgentTemplateAuthority;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener.EventReservationDirectory;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener.HumanCandidateDirectory;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener.HumanCandidatePolicyVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener.OutboundEventReservationVO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionProofVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelCompiler;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.DependencySnapshotVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.EventRegistrationVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.TemplateRegistrationVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationModelValidator.VersionedRefVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.DependencyResolver;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.SignedEnvelopeVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.TrustAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.TrustObservationVO;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.TrustedKeyVO;
import top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryAuthority;
import top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryProofVO;
import top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryProofVO.Status;
import top.egon.cola.platform.tianji.process.server.worker.WorkerEffectRecoveryQuery;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Test-classpath entry point for the isolated Process owner. */
@Slf4j
public final class AutomationProcessAcceptanceMain {
    private static final String FIXTURE = "tianji.automation.fixture.";
    private static final String ROLE = "PROCESS";
    private static final String PROCESS = "stellar.tianji.process.";
    private static final String SHA256 = "[0-9a-f]{64}";
    private static final String ID = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}";
    private static final String CANDIDATE_REF = "fixture-review";
    private static final String CANDIDATE_POLICY_SHA256 = "7".repeat(64);
    private static final String WORKER_AUTHORITY = "fixture-effect-authority";
    private static final Set<String> DATA_SOURCE_KEYS = Set.of("spring.datasource.url", "spring.datasource.username",
            "spring.datasource.password", "spring.datasource.driver-class-name", "spring.datasource.hikari.schema",
            "spring.datasource.hikari.maximum-pool-size", "spring.datasource.hikari.minimum-idle",
            "spring.datasource.hikari.connection-timeout", "spring.datasource.hikari.validation-timeout");
    private static final Set<String> DEFINITIONS = Set.of("fx_human", "fx_business", "fx_mixed", "fx_chain",
            "fx_control", "fx_version", "fx_recover", "fx_external");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final Map<String, Map<VersionedRefVO, EventRegistrationVO>> ROUTES = verifiedRoutes();

    private AutomationProcessAcceptanceMain() { }

    public static void main(String[] args) {
        try {
            Path privateRoot = Path.of("").toRealPath();
            Path config = fixtureConfiguration(privateRoot, args);
            Properties properties = privateProperties(config);
            FixtureSettings fixture = settings(privateRoot, properties);
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            var application = new SpringApplicationBuilder(FixtureConfiguration.class, TianjiProcessServerApplication.class)
                    .environment(environment).web(WebApplicationType.SERVLET)
                    .profiles("tianji-automation-acceptance-fixture")
                    .initializers(context -> context.getBeanFactory().registerSingleton("automationProcessFixtureSettings", fixture));
            try {
                var context = application.run("--spring.config.location=classpath:/application.yml," + config.toUri(),
                        "--spring.main.web-application-type=servlet");
                try {
                    installPostCommitBarrier(context.getBean(ProcessEngine.class), fixture.gateDirectory());
                    context.getBean("processEventApplicationService");
                    context.getBean("automationAgentDispatchRoutes", TypedRouteProvider.class);
                    context.getBean("tianjiProcessAgentBridgePoller");
                    if (!(context instanceof ServletWebServerApplicationContext web) || web.getWebServer() == null) {
                        throw unavailable("PROCESS_READY");
                    }
                    writeReady(fixture, web.getWebServer().getPort());
                } catch (Exception failure) {
                    context.close();
                    throw failure;
                }
            } catch (Exception failure) {
                log.error("AUTOMATION_PROCESS_BOOT_FAILED stage={}", stage(failure));
                throw unavailable("PROCESS_BOOT");
            }
        } catch (Exception failure) {
            log.error("AUTOMATION_PROCESS_FIXTURE_FAILED stage={}", stage(failure));
            throw unavailable("PROCESS_STARTUP");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("tianji-automation-acceptance-fixture")
    static class FixtureConfiguration {
        @Bean("automationReleaseDependencies")
        FixtureDependencies automationReleaseDependencies(@Qualifier("automationProcessFixtureSettings") FixtureSettings settings,
                @Qualifier("workerTopicRegistry") WorkerTopicRegistry workers) {
            return new FixtureDependencies(settings, workers);
        }

        @Bean("automationReleaseTrustAuthority")
        TrustAuthority automationReleaseTrustAuthority(
                @Qualifier("automationReleaseDependencies") FixtureDependencies fixtures) {
            return fixtures::trust;
        }

        @Bean("automationAdmissionAuthority")
        AutomationAdmissionAuthority automationAdmissionAuthority(
                @Qualifier("automationReleaseDependencies") FixtureDependencies fixtures,
                @Qualifier("automationReleaseStore") AutomationReleaseStore releases, DataSource processDataSource) {
            var database = new JdbcTemplate(processDataSource);
            database.setQueryTimeout(3);
            return query -> fixtures.admit(query, releases, database);
        }

        @Bean("automationAgentTemplateAuthority")
        AgentTemplateAuthority automationAgentTemplateAuthority(
                @Qualifier("automationProcessFixtureSettings") FixtureSettings settings,
                @Qualifier("automationAgentDispatchRoutes") TypedRouteProvider routes) {
            return (actor, sourceId, reference, configuration) -> {
                var route = routes.route(actor.tenantId());
                if (!settings.belongs(actor) || route == null || !route.sourceId().equals(sourceId) || configuration != null) {
                    throw denied();
                }
                var registered = settings.templates().get(new TemplateKey(actor.tenantId(), reference.key(), reference.version()));
                if (registered == null || !registered.reference().equals(reference)) throw denied();
                return AgentRuntimeTemplateCommand.fromExactWire(registered.command().toWire());
            };
        }

        @Bean("automationHumanCandidateDirectory")
        HumanCandidateDirectory automationHumanCandidateDirectory(
                @Qualifier("automationProcessFixtureSettings") FixtureSettings settings) {
            var policy = new HumanCandidatePolicyVO(Set.of("901"), CANDIDATE_POLICY_SHA256);
            return (tenantId, bindingRef, expectedSha) -> settings.tenants().containsKey(tenantId)
                    && CANDIDATE_REF.equals(bindingRef) && CANDIDATE_POLICY_SHA256.equals(expectedSha)
                            ? Optional.of(policy) : Optional.empty();
        }

        @Bean("processEventSourceAuthorizer")
        EventSourceAuthorizer processEventSourceAuthorizer(
                @Qualifier("automationProcessFixtureSettings") FixtureSettings settings) {
            return (actor, definition, release, route, version, sha) -> {
                var tenant = settings.tenants().get(actor.tenantId());
                var registered = ROUTES.getOrDefault(definition, Map.of()).get(new VersionedRefVO(route, version));
                return tenant != null && tenant.producer().equals(actor) && registered != null
                        && registered.contractSha256().equals(sha);
            };
        }

        @Bean("automationEventReservationDirectory")
        EventReservationDirectory automationEventReservationDirectory(
                @Qualifier("automationProcessFixtureSettings") FixtureSettings settings, DataSource processDataSource) {
            var database = new JdbcTemplate(processDataSource);
            database.setQueryTimeout(3);
            var reservation = new OutboundEventReservationVO("fxSignal", 1L, "eventWait", "waitToken");
            return (tenantId, compiledSha, workerNode, capability, version) -> {
                if (!settings.tenants().containsKey(tenantId) || !"external".equals(workerNode)
                        || !"fixtureExternal".equals(capability) || version != 1L || !compiledSha.matches(SHA256)) {
                    return Optional.empty();
                }
                Boolean registered = database.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM tianji_process.tianji_process_automation_release
                            WHERE tenant_id = ? AND root_definition_key = 'fx_business' AND compiled_sha256 = ?
                        )
                        """, Boolean.class, tenantId, compiledSha);
                return Boolean.TRUE.equals(registered) ? Optional.of(reservation) : Optional.empty();
            };
        }

        @Bean(value = "workerEffectRecoveryAuthority", destroyMethod = "close")
        FixtureWorkerEvidence workerEffectRecoveryAuthority(
                @Qualifier("automationProcessFixtureSettings") FixtureSettings settings) {
            return new FixtureWorkerEvidence(settings);
        }
    }

    static final class FixtureDependencies implements DependencyResolver {
        private final FixtureSettings fixtures;
        private final WorkerTopicRegistry workers;
        private final AutomationModelValidator validator = new AutomationModelValidator();
        private final AutomationModelCompiler compiler = new AutomationModelCompiler();

        FixtureDependencies(FixtureSettings fixtures, WorkerTopicRegistry workers) {
            this.fixtures = fixtures;
            this.workers = workers;
        }

        @Override
        public DependencySnapshotVO resolve(ProcessActor actor, AutomationModelSourceVO source) {
            if (!fixtures.belongs(actor)) throw denied();
            String definition = source.models().getFirst().definitionKey();
            if (!DEFINITIONS.contains(definition)) throw denied();
            var templates = new HashMap<VersionedRefVO, TemplateRegistrationVO>();
            fixtures.templates().forEach((key, template) -> {
                if (key.tenantId() == actor.tenantId()) {
                    templates.put(new VersionedRefVO(key.key(), key.version()), template.registration());
                }
            });
            return new DependencySnapshotVO(actor.tenantId(), workers.automationReleaseRegistrations(actor.tenantId()),
                    templates, Set.of(), Map.of(CANDIDATE_REF, CANDIDATE_POLICY_SHA256),
                    ROUTES.getOrDefault(definition, Map.of()));
        }

        @Override
        public boolean isCurrent(ProcessActor actor, AutomationModelSourceVO source, DependencySnapshotVO snapshot) {
            return resolve(actor, source).equals(snapshot);
        }

        AutomationAdmissionProofVO admit(AutomationAdmissionQuery query, AutomationReleaseStore releases, JdbcTemplate database) {
            requireOutsideTransaction();
            var actor = query.getActor();
            var tenant = fixtures.tenants().get(actor.tenantId());
            if (tenant == null || !DEFINITIONS.contains(query.getDefinitionKey()) || !tenant.permits(query)) throw denied();
            if (query.getPurpose() == AutomationAdmissionQuery.Purpose.EVENT_START) awaitEventGate(fixtures.gateDirectory(), query);
            var released = releases.find(actor.tenantId(), query.getReleaseVersion())
                    .orElseThrow(() -> unavailable("RELEASE_NOT_FOUND"));
            var observation = trust(query, released);
            Instant now = Objects.requireNonNull(database.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class))
                    .toInstant().truncatedTo(ChronoUnit.MICROS);
            Instant until = min(now.plusSeconds(25), observation.envelope().expiresAt());
            String querySha256 = query.sha256();
            String authorizationSha256 = ProcessTypedJson.sha256("AUTOMATION_FIXTURE_AUTHORIZATION_V1",
                    ProcessTypedJson.canonicalBytes(List.of(fixtures.runId(), querySha256)));
            return new AutomationAdmissionProofVO(querySha256, "fixture-" + querySha256, authorizationSha256,
                    now, until, observation.currentGeneration(), observation.envelopeSha256(), observation.payloadSha256(),
                    observation.dependencyEvidenceSha256(query), Map.of("authorizationSourceRef", "fixture-process-policy",
                            "authorizationVersion", "1", "dependencySourceRef", "fixture-registered-dependencies",
                            "trustSourceRef", "fixture-release-signing-key"));
        }

        TrustObservationVO trust(AutomationAdmissionQuery query, ImmutableReleaseVO release) {
            requireOutsideTransaction();
            if (query.getActor().tenantId() != release.tenantId() || !query.getDefinitionKey().equals(release.rootDefinitionKey())
                    || !query.getReleaseVersion().equals(release.releaseVersion())
                    || !query.getSourceManifestSha256().equals(release.sourceSha256())
                    || !query.getCompiledManifestSha256().equals(release.compiledSha256())
                    || !query.getDependencyManifestSha256().equals(release.dependencySha256())) {
                throw unavailable("RELEASE_BINDING");
            }
            var snapshot = resolve(query.getActor(), release.source());
            var exact = compiler.compile(validator.validate(release.source(), snapshot));
            if (!exact.dependencySha256().equals(release.dependencySha256())
                    || !exact.compiledSha256().equals(release.compiledSha256())) throw unavailable("RELEASE_DEPENDENCIES");
            return fixtures.signer().observe(release, exact.dependencyManifest());
        }
    }

    static final class FixtureWorkerEvidence implements WorkerEffectRecoveryAuthority, AutoCloseable {
        private static final Set<String> FIELDS = Set.of("dispatchGeneration", "inputSha256", "applied", "businessReceiptId", "observedAt");
        private final FixtureSettings settings;
        private final HttpClient http;

        FixtureWorkerEvidence(FixtureSettings settings) {
            this.settings = settings;
            http = HttpClient.newBuilder().sslContext(settings.tls()).connectTimeout(Duration.ofSeconds(2))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        }

        @Override
        public WorkerEffectRecoveryProofVO observe(WorkerEffectRecoveryQuery query) {
            requireOutsideTransaction();
            if (!settings.belongs(query.getActor()) || !"fixture-worker".equals(query.getSourceId())
                    || !"fixtureExternal".equals(query.getCapabilityKey()) || query.getCapabilityVersion() != 1L
                    || !"fixture-worker-v1".equals(query.getHandlerVersion()) || !"fixture-external".equals(query.getTargetId())
                    || !"b94e50c2b6127cd58fb23cb393cd16892b1dcc72643edbfbb8f14ca8fa9d4b54".equals(query.getOutputSchemaSha256())) {
                return unknown(query);
            }
            try {
                var body = JSON.writeValueAsBytes(Map.of("tenantId", query.getActor().tenantId(),
                        "effectKey", query.getEffectKey(), "effectFingerprint", query.getEffectFingerprint()));
                var request = HttpRequest.newBuilder(settings.externalTarget().resolve("/observe")).timeout(Duration.ofSeconds(2))
                        .header("Content-Type", "application/json").header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var content = response.body()) {
                    if (response.statusCode() != 200) return unknown(query);
                    return checkedObservation(query, JSON.readValue(readBounded(content, 4096), OBJECT));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return unknown(query);
            } catch (IOException | RuntimeException noAuthoritativeObservation) {
                return unknown(query);
            }
        }

        private WorkerEffectRecoveryProofVO checkedObservation(WorkerEffectRecoveryQuery query, Map<String, Object> wire) {
            if (!FIELDS.equals(wire.keySet()) || !(wire.get("inputSha256") instanceof String inputSha256)
                    || !inputSha256.matches(SHA256) || !(wire.get("applied") instanceof Boolean applied)
                    || !(wire.get("observedAt") instanceof String observed)) return unknown(query);
            long generation = exactLong(wire.get("dispatchGeneration"));
            Object reference = wire.get("businessReceiptId");
            if (generation < 1 || applied && (!(reference instanceof String receiptId) || !receiptId.matches(ID))
                    || !applied && reference != null) return unknown(query);
            String resultRef = (String) reference;
            Instant now = Instant.parse(observed);
            Instant until = min(now.plusSeconds(25), query.getNodeDeadlineAt());
            if (!until.isAfter(now)) return unknown(query);
            String evidence = ProcessTypedJson.sha256("AUTOMATION_FIXTURE_EFFECT_EVIDENCE_V1", ProcessTypedJson.canonicalBytes(
                    Arrays.asList(settings.runId(), query.getActor().tenantId(), query.getEffectKey(), generation, inputSha256, applied, resultRef)));
            Map<String, Object> output = applied ? Map.of("accepted", true) : null;
            String resultSha = applied ? AutomationWorkerBusinessResultDTO.resultSha256(query.getEffectFingerprint(), resultRef, output) : null;
            return new WorkerEffectRecoveryProofVO(applied ? Status.APPLIED : Status.NOT_APPLIED_FINAL, query.sha256(), WORKER_AUTHORITY,
                    query.getEffectKey(), query.getEffectFingerprint(), query.getTargetId(), generation, now, until,
                    evidence, resultRef, output, resultSha);
        }

        private static WorkerEffectRecoveryProofVO unknown(WorkerEffectRecoveryQuery query) {
            return new WorkerEffectRecoveryProofVO(Status.UNVERIFIABLE, query.sha256(), null, query.getEffectKey(),
                    query.getEffectFingerprint(), query.getTargetId(), null, null, null, null, null, null, null);
        }

        @Override
        public void close() {
            http.close();
        }
    }

    private static Map<String, Map<VersionedRefVO, EventRegistrationVO>> verifiedRoutes() {
        var value = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of("value", Map.of("type", "string")),
                "required", List.of("value"), "additionalProperties", false));
        var approved = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of("approved", Map.of("type", "boolean")),
                "required", List.of("approved"), "additionalProperties", false));
        var business = startRoute("fx_business", value);
        var version = startRoute("fx_version", value);
        var signal = new EventRouteVO("fxSignal", 1L, "FixtureSignaled", "SIGNAL", "fixture-events", approved,
                List.of(new MappingVO("approved", "event.approved")), Duration.ofSeconds(60), null, "eventWait", null);
        return Map.of("fx_business", Map.of(new VersionedRefVO("fxStart", 1L), registerRoute("fx_business", business),
                        new VersionedRefVO("fxSignal", 1L), registerRoute("fx_business", signal)),
                "fx_version", Map.of(new VersionedRefVO("fxStart", 1L), registerRoute("fx_version", version)));
    }

    private static EventRouteVO startRoute(String definition, ProcessSchemaVO payload) {
        return new EventRouteVO("fxStart", 1L, "FixtureCreated", "START", "fixture-events", payload,
                List.of(new MappingVO("value", "event.value")), Duration.ofSeconds(60), definition, null, null);
    }

    private static EventRegistrationVO registerRoute(String definition, EventRouteVO route) {
        var wire = JSON.convertValue(route, OBJECT);
        String digest = ProcessTypedJson.sha256("AUTOMATION_FIXTURE_EVENT_REGISTRATION_V1",
                ProcessTypedJson.canonicalBytes(Map.of("definitionKey", definition, "route", wire)));
        return new EventRegistrationVO(route, digest);
    }

    private static void awaitEventGate(Path directory, AutomationAdmissionQuery query) {
        String eventId = query.getEventIdentity().eventId();
        if (!eventId.matches(ID)) throw denied();
        awaitGate(directory.resolve("process-event-" + eventId), Duration.ofSeconds(45));
    }

    private static void awaitGate(Path marker, Duration timeout) {
        long limit = System.nanoTime() + timeout.toNanos();
        while (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (System.nanoTime() - limit >= 0) throw unavailable("EVENT_FIXTURE_GATE");
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw unavailable("EVENT_FIXTURE_INTERRUPTED");
            }
        }
    }

    private static void installPostCommitBarrier(ProcessEngine engine, Path gateDirectory) {
        if (!(engine.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration spring)
                || !(spring.getTransactionManager() instanceof DataSourceTransactionManager transactions)
                || transactions.getDataSource() == null) throw unavailable("PROCESS_NATIVE_TRANSACTION");
        var dataSource = transactions.getDataSource();
        engine.getRuntimeService().addEventListener(new FlowableEventListener() {
            @Override
            public void onEvent(FlowableEvent event) {
                if (!(event instanceof FlowableEngineEvent started) || started.getProcessInstanceId() == null
                        || !TransactionSynchronizationManager.isSynchronizationActive()) return;
                String instanceId = started.getProcessInstanceId();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int completionStatus) {
                        if (completionStatus != STATUS_COMMITTED) return;
                        String receiptId = committedEventReceipt(dataSource, instanceId);
                        if (receiptId != null && receiptId.matches(ID)) {
                            awaitGate(gateDirectory.resolve("process-event-after-native-" + receiptId), Duration.ofSeconds(85));
                        }
                    }
                });
            }

            @Override public boolean isFailOnException() { return true; }
            @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
            @Override public String getOnTransaction() { return null; }
        }, FlowableEngineEventType.PROCESS_STARTED);
    }

    private static String committedEventReceipt(DataSource dataSource, String instanceId) {
        // This connection observes a durable binding independently of the just-completed native transaction.
        try (var connection = dataSource.getConnection(); var query = connection.prepareStatement("""
                SELECT source_event_receipt_id FROM tianji_process.tianji_process_instance_binding
                WHERE instance_id = ?
                """)) {
            query.setQueryTimeout(3);
            query.setString(1, instanceId);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                String receipt = rows.getString(1);
                if (rows.next()) throw unavailable("EVENT_FIXTURE_BINDING");
                return receipt;
            }
        } catch (SQLException unavailable) {
            throw unavailable("EVENT_FIXTURE_DATABASE");
        }
    }

    private record TenantPolicy(long tenantId, String issuer, ProcessActor activator, ProcessActor requester, ProcessActor producer) {
        boolean permits(AutomationAdmissionQuery query) {
            return switch (query.getPurpose()) {
                case ACTIVATE, REPLAY_ACTIVATION -> activator.equals(query.getActor());
                case START, REPLAY_START -> requester.equals(query.getActor());
                case EVENT_ADMIT, EVENT_START -> producer.equals(query.getActor()) && query.getEventIdentity() != null
                        && producer.issuerFingerprint().equals(query.getEventIdentity().producerIssuer())
                        && producer.subjectId().equals(query.getEventIdentity().producerSubject());
            };
        }
    }

    private record ProcessCatalog(List<TenantEntry> tenants) { }
    private record TenantEntry(long tenantId, String issuer, String activatorSubject, String requesterSubject, String eventProducerSubject) {
        TenantPolicy policy() {
            requireLoopbackHttps(URI.create(issuer), false);
            return new TenantPolicy(tenantId, issuer, ProcessActor.verified(tenantId, ProcessActor.SubjectType.USER, activatorSubject, issuer),
                    ProcessActor.verified(tenantId, ProcessActor.SubjectType.USER, requesterSubject, issuer),
                    ProcessActor.verified(tenantId, ProcessActor.SubjectType.SERVICE, eventProducerSubject, issuer));
        }
    }

    private record TemplateCatalog(List<TemplateEntry> templates) { }
    private record TemplateEntry(long tenantId, String issuer, String publisherSubject, Map<String, Object> command) { }
    private record TemplateKey(long tenantId, String key, long version) { }

    private record RegisteredTemplate(TemplateRefVO reference, AgentRuntimeTemplateCommand command, TemplateRegistrationVO registration) {
        static RegisteredTemplate from(Map<String, Object> wire) {
            var command = AgentRuntimeTemplateCommand.fromExactWire(wire);
            var reference = new TemplateRefVO(command.getTemplateKey(), command.getTemplateVersion(), command.contentSha256());
            return new RegisteredTemplate(reference, command, new TemplateRegistrationVO(reference,
                    ProcessNodeLifecycleListener.schemaSha256(command.getInputSchema()),
                    ProcessNodeLifecycleListener.schemaSha256(command.getOutputSchema()),
                    ProcessNodeLifecycleListener.schemaSha256(command.getParameterSchema())));
        }
    }

    static record FixtureSettings(String runId, String generation, Path readyFile, Path gateDirectory, String candidateSha,
            SSLContext tls, URI externalTarget, ReleaseSigner signer, Map<Long, TenantPolicy> tenants,
            Map<TemplateKey, RegisteredTemplate> templates) {
        boolean belongs(ProcessActor actor) {
            var tenant = actor == null ? null : tenants.get(actor.tenantId());
            return tenant != null && tenant.producer().issuerFingerprint().equals(actor.issuerFingerprint());
        }
    }

    private record ReleaseSigner(PrivateKey privateKey, String encodedPublicKey, String keyId) {
        TrustObservationVO observe(ImmutableReleaseVO release, Map<String, Object> dependencies) {
            try {
                var issued = release.createdAt().truncatedTo(ChronoUnit.MICROS);
                var expires = issued.plus(Duration.ofHours(4));
                var key = new TrustedKeyVO(keyId, release.tenantId(), 1L, encodedPublicKey, issued.minusSeconds(1), expires);
                var signature = Signature.getInstance("Ed25519");
                signature.initSign(privateKey);
                signature.update(AutomationReleaseTrustVerifier.signingPayload(release, keyId, issued, expires));
                var envelope = new SignedEnvelopeVO(keyId, issued, expires, Base64.getEncoder().encodeToString(signature.sign()));
                return new TrustObservationVO(key, 1L, envelope, dependencies,
                        AutomationReleaseTrustVerifier.payloadSha256(AutomationReleaseTrustVerifier.canonicalPayload(release)));
            } catch (Exception signingFailure) {
                throw unavailable("RELEASE_SIGNING");
            }
        }
    }

    private static FixtureSettings settings(Path root, Properties properties) throws Exception {
        requirePrivateDirectory(root, root);
        requirePrivateDirectory(root, Path.of(required(properties, FIXTURE + "gate-directory")));
        verifyProductConfiguration(root, properties);
        String runId = required(properties, FIXTURE + "run-id");
        String generation = required(properties, FIXTURE + "generation");
        String candidateSha = required(properties, FIXTURE + "candidate-sha256");
        if (!runId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || !generation.matches("[1-9][0-9]{0,8}")
                || !candidateSha.matches(SHA256) || !ROLE.equals(required(properties, FIXTURE + "role"))
                || !"true".equals(required(properties, FIXTURE + "run-authorized"))) throw unavailable("ROLE_CONFIG");
        Path ready = Path.of(required(properties, FIXTURE + "ready-file"));
        if (!ready.isAbsolute() || !ready.equals(ready.normalize()) || !ready.startsWith(root)
                || Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) throw unavailable("READY_PATH");
        requirePrivateDirectory(root, ready.getParent());
        Path gate = Path.of(required(properties, FIXTURE + "gate-directory"));
        var tls = trustContext(root, properties);
        URI target = URI.create(required(properties, FIXTURE + "external-target"));
        requireLoopbackHttps(target, true);
        var signer = releaseSigner(root, properties);
        var tenants = configuredTenants(root, properties);
        var templates = configuredTemplates(root, properties, tenants);
        return new FixtureSettings(runId, generation, ready, gate, candidateSha, tls, target, signer, tenants, templates);
    }

    private static Map<Long, TenantPolicy> configuredTenants(Path root, Properties properties) throws IOException {
        var table = JSON.readValue(privateBytes(root, Path.of(required(properties, FIXTURE + "process-catalog")), 64 * 1024),
                ProcessCatalog.class);
        if (table.tenants() == null || table.tenants().size() != 2) throw unavailable("TENANT_CATALOG");
        var entries = new HashMap<Long, TenantPolicy>();
        for (TenantEntry configured : table.tenants()) {
            var policy = configured.policy();
            if (entries.putIfAbsent(policy.tenantId(), policy) != null) throw unavailable("TENANT_CATALOG");
        }
        if (entries.values().stream().map(TenantPolicy::issuer).distinct().count() != 2) throw unavailable("TENANT_CATALOG");
        return Map.copyOf(entries);
    }

    private static Map<TemplateKey, RegisteredTemplate> configuredTemplates(Path root, Properties properties,
            Map<Long, TenantPolicy> tenants) throws IOException {
        var table = JSON.readValue(privateBytes(root, Path.of(required(properties, FIXTURE + "template-catalog")), 256 * 1024),
                TemplateCatalog.class);
        if (table.templates() == null || table.templates().isEmpty() || table.templates().size() > 32) {
            throw unavailable("TEMPLATE_CATALOG");
        }
        var entries = new HashMap<TemplateKey, RegisteredTemplate>();
        for (TemplateEntry entry : table.templates()) {
            var tenant = tenants.get(entry.tenantId());
            if (tenant == null || !tenant.issuer().equals(entry.issuer()) || !entry.publisherSubject().matches(ID)) {
                throw unavailable("TEMPLATE_CATALOG");
            }
            var template = RegisteredTemplate.from(entry.command());
            var key = new TemplateKey(tenant.tenantId(), template.reference().key(), template.reference().version());
            if (entries.putIfAbsent(key, template) != null) throw unavailable("TEMPLATE_CATALOG");
        }
        if (entries.keySet().stream().map(TemplateKey::tenantId).distinct().count() != tenants.size()) {
            throw unavailable("TEMPLATE_CATALOG");
        }
        return Map.copyOf(entries);
    }

    private static ReleaseSigner releaseSigner(Path root, Properties settings) throws Exception {
        byte[] privateKey = privateBytes(root, Path.of(required(settings, FIXTURE + "release-signing-private-key")), 1024);
        byte[] publicKey = privateBytes(root, Path.of(required(settings, FIXTURE + "release-signing-public-key")), 1024);
        try {
            var factory = KeyFactory.getInstance("Ed25519");
            var privateSigningKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateKey));
            var publicSigningKey = factory.generatePublic(new X509EncodedKeySpec(publicKey));
            byte[] check = "TIANJI_FIXTURE_RELEASE_KEY_PAIR".getBytes(StandardCharsets.UTF_8);
            var signature = Signature.getInstance("Ed25519");
            signature.initSign(privateSigningKey);
            signature.update(check);
            byte[] signed = signature.sign();
            signature.initVerify(publicSigningKey);
            signature.update(check);
            if (!signature.verify(signed)) throw unavailable("RELEASE_KEY_PAIR");
            byte[] canonicalPublicKey = publicSigningKey.getEncoded();
            return new ReleaseSigner(privateSigningKey, Base64.getEncoder().encodeToString(canonicalPublicKey),
                    AutomationReleaseTrustVerifier.payloadSha256(canonicalPublicKey));
        } finally {
            Arrays.fill(privateKey, (byte) 0);
        }
    }

    private static SSLContext trustContext(Path root, Properties settings) throws Exception {
        var trustStore = Path.of(required(settings, FIXTURE + "trust-store"));
        requirePrivateFile(root, trustStore, 1024 * 1024);
        char[] password = new String(privateBytes(root, Path.of(required(settings, FIXTURE + "trust-password-file")), 1024),
                StandardCharsets.UTF_8).strip().toCharArray();
        try {
            if (password.length == 0) throw unavailable("FIXTURE_TLS_PASSWORD");
            var store = KeyStore.getInstance(trustStore.toFile(), password);
            var names = java.util.Collections.list(store.aliases());
            if (names.isEmpty()) throw unavailable("FIXTURE_TLS_EMPTY");
            for (String alias : names) {
                if (!store.isCertificateEntry(alias)) throw unavailable("FIXTURE_TLS_CERTIFICATES");
            }
            var manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            manager.init(store);
            var context = SSLContext.getInstance("TLS");
            context.init(null, manager.getTrustManagers(), null);
            SSLContext.setDefault(context);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
            return context;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void verifyProductConfiguration(Path root, Properties options) throws IOException {
        String database = required(options, "spring.datasource.url");
        String user = required(options, "spring.datasource.username");
        if (!database.matches("jdbc:postgresql://127[.]0[.]0[.]1:[1-9][0-9]{0,4}/tianji_it_automation_ps_[0-9a-f]{12}")
                || !user.equals("tianji_it_automation_ps_runtime_" + database.substring(database.length() - 12))
                || !"127.0.0.1".equals(required(options, "server.address")) || !"0".equals(required(options, "server.port"))
                || !"true".equals(required(options, "server.ssl.enabled"))
                || !"false".equals(required(options, "flowable.database-schema-update"))
                || !"false".equals(required(options, "spring.flyway.enabled"))
                || !"never".equals(required(options, "spring.sql.init.mode"))) throw unavailable("PRODUCT_CONFIG");
        requirePrivateFile(root, Path.of(URI.create(required(options, "server.ssl.key-store"))), 4 * 1024 * 1024);
        for (String feature : List.of("api.enabled", "automation.enabled", "events.enabled", "events.admission-enabled",
                "worker.enabled", "human-tasks.enabled", "agent-bridge.enabled", "agent-results.enabled")) {
            if (!"true".equals(required(options, PROCESS + feature))) throw unavailable("PRODUCT_FEATURES");
        }
        for (String key : options.stringPropertyNames()) {
            String value = options.getProperty(key);
            if (key.startsWith("spring.config.") || key.startsWith("spring.profiles.") || value.contains("${")
                    || key.startsWith("spring.datasource.") && !DATA_SOURCE_KEYS.contains(key)
                    || "management.server.port".equals(key) && !"-1".equals(value)
                    || "management.server.address".equals(key) && !"127.0.0.1".equals(value)
                    || key.endsWith("database-schema-update") && !"false".equals(value)) {
                throw unavailable("PRODUCT_CONFIG_OVERRIDE");
            }
            if (value.startsWith("http://") || value.startsWith("https://")) {
                // resource-uri is an OAuth audience identifier; the product does not connect to it.
                if (!key.endsWith("resource-uri") && !key.endsWith("audience")) requireLoopbackHttps(URI.create(value), false);
            }
        }
    }

    private static void requireLoopbackHttps(URI uri, boolean originOnly) {
        if (!"https".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1 || uri.getPort() > 65535
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.normalize().equals(uri)
                || originOnly && !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw unavailable("LOOPBACK_HTTPS");
    }

    private static Path fixtureConfiguration(Path root, String[] args) throws IOException {
        String prefix = "--spring.config.additional-location=";
        if (args.length != 1 || !args[0].startsWith(prefix)) throw unavailable("PRIVATE_CONFIG_ARGUMENT");
        Path file = Path.of(URI.create(args[0].substring(prefix.length())));
        if (!file.isAbsolute() || file.getParent() == null) throw unavailable("PRIVATE_CONFIG_ARGUMENT");
        requirePrivateDirectory(root, root);
        requirePrivateDirectory(root, file.getParent());
        requirePrivateFile(root, file, 128 * 1024);
        return file;
    }

    private static Properties privateProperties(Path file) throws IOException {
        var properties = new Properties();
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            properties.load(input);
        }
        return properties;
    }

    private static byte[] privateBytes(Path root, Path path, int maximum) throws IOException {
        requirePrivateFile(root, path, maximum);
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return readBounded(input, maximum);
        }
    }

    private static byte[] readBounded(java.io.InputStream input, int maximum) throws IOException {
        byte[] bytes = input.readNBytes(maximum + 1);
        if (bytes.length == 0 || bytes.length > maximum) throw unavailable("FIXTURE_INPUT_SIZE");
        return bytes;
    }

    private static void requirePrivateFile(Path root, Path path, long maximum) throws IOException {
        if (!path.isAbsolute() || !path.startsWith(root) || !path.normalize().equals(path) || !path.toRealPath().equals(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !Files.getOwner(root).equals(Files.getOwner(path))
                || !Files.getPosixFilePermissions(path).equals(PosixFilePermissions.fromString("rw-------"))
                || Files.size(path) == 0 || Files.size(path) > maximum) throw unavailable("PRIVATE_FIXTURE_FILE");
    }

    private static void requirePrivateDirectory(Path root, Path path) throws IOException {
        if (!path.isAbsolute() || !path.startsWith(root) || !path.toRealPath().equals(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || !Files.getOwner(root).equals(Files.getOwner(path))
                || !Files.getPosixFilePermissions(path).equals(PosixFilePermissions.fromString("rwx------"))) {
            throw unavailable("PRIVATE_FIXTURE_DIRECTORY");
        }
    }

    private static void writeReady(FixtureSettings fixture, int port) throws IOException {
        if (port < 1 || port > 65535) throw unavailable("READY_PORT");
        var file = fixture.readyFile();
        Path temporary = Files.createTempFile(file.getParent(), "process-ready-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            String value = "runId=" + fixture.runId() + "\nrole=" + ROLE + "\ngeneration=" + fixture.generation()
                    + "\npid=" + ProcessHandle.current().pid() + "\nport=" + port + "\ncandidateSha256=" + fixture.candidateSha() + "\n";
            Files.writeString(temporary, value, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw unavailable("MISSING_PROPERTY_" + key);
        return value;
    }

    private static long exactLong(Object number) {
        if (number instanceof Integer value) return value.longValue();
        if (number instanceof Long value) return value;
        return -1;
    }

    private static Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }

    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw unavailable("FIXTURE_TRANSACTION");
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("Fixture Process actor or registration is not approved"); }

    private static String stage(Exception failure) {
        return failure instanceof FixtureFailure fixture ? fixture.code : failure.getClass().getSimpleName();
    }

    private static IllegalStateException unavailable(String code) {
        return new FixtureFailure(code);
    }

    private static final class FixtureFailure extends IllegalStateException {
        private final String code;

        private FixtureFailure(String code) {
            super("Automation Process acceptance fixture: " + code);
            this.code = code;
        }
    }
}
