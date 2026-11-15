package top.egon.cola.platform.tianji.worker.acceptance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.client.HttpAutomationWorkerClient;
import top.egon.cola.platform.tianji.client.HttpProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.worker.AutomationWorkerExecutionContextVO;
import top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerDescriptor;
import top.egon.cola.platform.tianji.worker.AutomationWorkerHandlerRegistration;
import top.egon.cola.platform.tianji.worker.JdbcWorkerExecutionStore;
import top.egon.cola.platform.tianji.worker.RemoteWorkerPoller;
import top.egon.cola.platform.tianji.worker.TianjiWorkerAutoConfiguration;
import top.egon.cola.platform.tianji.worker.WorkerEffectEvidenceVO;
import top.egon.cola.platform.tianji.worker.WorkerEffectOutcomeResolver;
import top.egon.cola.platform.tianji.worker.WorkerEffectReconcileQuery;
import top.egon.cola.platform.tianji.worker.WorkerExecutionStore;
import top.egon.cola.platform.tianji.worker.WorkerHandlerFailureException;
import top.egon.cola.platform.tianji.worker.WorkerPollingProperties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Entry point for an isolated business Worker using its own starter classpath. */
@Slf4j
public final class AutomationWorkerAcceptanceMain {
    private static final String FIXTURE = "tianji.automation.fixture.";
    private static final String POLLING = "stellar.tianji.worker.";
    private static final String LOCATION = "--spring.config.additional-location=";
    private static final String SOURCE_ID = "fixture-worker";
    private static final String HANDLER_VERSION = "fixture-worker-v1";
    private static final String EXTERNAL = "fixtureExternal";
    private static final String EXTERNAL_TARGET = "fixture-external";
    private static final String AUTHORITY = "fixture-effect-authority";
    private static final String INPUT_SCHEMA_SHA256 = "005130f2b802ebdab22de80606f8e829a5b9f69e95e3587e6529bf31f2e77d24";
    private static final String OUTPUT_SCHEMA_SHA256 = "b94e50c2b6127cd58fb23cb393cd16892b1dcc72643edbfbb8f14ca8fa9d4b54";
    private static final Map<String, Object> ACCEPTED = Map.of("accepted", true);
    private static final Map<String, Boolean> LOOKUP = Map.of("fixture", true, "known", true, "missing", false);
    private static final int MAX_TARGET_BODY_BYTES = 2_048;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration WORK_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern JDBC = Pattern.compile("jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_automation_w_([0-9a-f]{12}))");
    private static final Set<String> FIXTURE_FIELDS = Set.of("run-authorized", "run-id", "role", "generation",
            "ready-file", "gate-directory", "candidate-sha256", "trust-store", "trust-password-file", "process-relay",
            "external-target", "worker-acquire-token-file", "worker-report-token-file");
    private static final Set<String> POLLING_FIELDS = Set.of("enabled", "tenant-id", "concurrency", "poll-interval",
            "acknowledgement-reserve", "max-clock-skew", "acknowledgement-retry-delay", "acknowledgement-attempts", "shutdown-timeout");
    private static final Set<String> HOST_FIELDS = Set.of("spring.datasource.url", "spring.datasource.username",
            "spring.datasource.password", "spring.flyway.enabled", "spring.sql.init.mode");
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final ProcessSchemaVO INPUT = ProcessSchemaVO.fromWire(Map.of("type", "object", "additionalProperties", false,
            "required", List.of(), "properties", Map.of("value", Map.of("type", "string"), "waitToken", Map.of("type", "string"))));
    private static final ProcessSchemaVO OUTPUT = ProcessSchemaVO.fromWire(Map.of("type", "object", "additionalProperties", false,
            "required", List.of("accepted"), "properties", Map.of("accepted", Map.of("type", "boolean"))));

    private AutomationWorkerAcceptanceMain() { }

    public static void main(String[] args) throws Exception {
        FixtureSettingsVO settings = settings(args);
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("automationWorkerFixture", settings.polling()));

        var application = new SpringApplicationBuilder(FixtureConfiguration.class).web(WebApplicationType.NONE)
                .environment(environment).logStartupInfo(false)
                .initializers(context -> context.getBeanFactory().registerSingleton("automationWorkerFixtureSettings", settings));
        try (var context = application.run()) {
            var readiness = context.getBean(WorkerExecutionStore.class).automationReadiness().orElseThrow(
                    () -> unavailable("WORKER_FIXTURE_STORAGE_NOT_READY"));
            if (!SOURCE_ID.equals(readiness.sourceId()) || readiness.tenantId() != settings.tenantId()
                    || !context.getBean(RemoteWorkerPoller.class).isRunning()) throw unavailable("WORKER_FIXTURE_POLLER_NOT_READY");
            publishReady(settings);
            log.info("WORKER_FIXTURE_READY");
            new CountDownLatch(1).await();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    @Configuration(value = "automationWorkerFixtureConfiguration", proxyBeanMethods = false)
    @Import(TianjiWorkerAutoConfiguration.class)
    static class FixtureConfiguration {
        @Bean("automationWorkerDataSource")
        DataSource dataSource(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings) {
            var source = new DriverManagerDataSource(settings.jdbcUrl(), settings.username(), settings.password());
            var connectionProperties = new Properties();
            connectionProperties.setProperty("connectTimeout", "5");
            connectionProperties.setProperty("socketTimeout", "10");
            source.setConnectionProperties(connectionProperties);
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(5);
            String database = settings.jdbcUrl().substring(settings.jdbcUrl().lastIndexOf('/') + 1);
            Boolean isolated = jdbc.queryForObject("""
                    SELECT current_database()=? AND current_user=? AND inet_server_addr()='127.0.0.1'::inet
                        AND rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication
                        AND NOT rolbypassrls AND NOT has_database_privilege(current_database(),'CREATE')
                        AND has_schema_privilege('automation_fixture','USAGE')
                        AND NOT has_schema_privilege('automation_fixture','CREATE')
                        AND has_table_privilege('automation_fixture.target_dispatch','SELECT')
                        AND has_table_privilege('automation_fixture.business_effect','SELECT')
                        AND has_table_privilege('automation_fixture.business_effect','INSERT')
                    FROM pg_roles WHERE rolname=current_user
                    """, Boolean.class, database, settings.username());
            if (!Boolean.TRUE.equals(isolated)) throw unavailable("WORKER_FIXTURE_DATABASE_NOT_ISOLATED");
            return source;
        }

        @Bean("automationWorkerTransactionManager")
        DataSourceTransactionManager transactionManager(@Qualifier("automationWorkerDataSource") DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean("automationWorkerExecutionStore")
        WorkerExecutionStore store(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings,
                @Qualifier("automationWorkerDataSource") DataSource source,
                @Qualifier("automationWorkerTransactionManager") DataSourceTransactionManager manager,
                @Qualifier("automationWorkerEffectResolver") WorkerEffectOutcomeResolver resolver,
                WorkerPollingProperties polling) {
            return new JdbcWorkerExecutionStore(source, manager, "tianji_worker", SOURCE_ID, settings.tenantId(), WORK_TIMEOUT,
                    polling.acknowledgementReserve().plus(polling.maxClockSkew()), resolver);
        }

        @Bean("automationWorkerEffectResolver")
        WorkerEffectOutcomeResolver resolver(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings,
                @Qualifier("automationWorkerDataSource") DataSource source) {
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(5);
            return query -> observe(settings, jdbc, query);
        }

        @Bean(name = "automationWorkerHttp", destroyMethod = "close")
        HttpClient http(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings) {
            return HttpClient.newBuilder().sslContext(settings.trust()).connectTimeout(HTTP_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY).build();
        }

        @Bean("automationLegacyWorkerClient")
        WorkerClient legacyClient(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings,
                @Qualifier("automationWorkerHttp") HttpClient http) {
            return new HttpProcessClient(settings.process(), http, () -> token(settings, settings.acquireToken()), HTTP_TIMEOUT);
        }

        @Bean(name = "automationWorkerClient", destroyMethod = "close")
        HttpAutomationWorkerClient automationClient(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings,
                @Qualifier("automationWorkerHttp") HttpClient http) {
            return new HttpAutomationWorkerClient(settings.process(), http, () -> token(settings, settings.acquireToken()),
                    () -> token(settings, settings.reportToken()), HTTP_TIMEOUT, settings.tenantId(), SOURCE_ID);
        }

        @Bean("tianjiWorkerScheduler")
        ThreadPoolTaskScheduler workerScheduler() {
            var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setThreadNamePrefix("automation-worker-poll-");
            return scheduler;
        }

        @Bean("tianjiWorkerExecutor")
        ThreadPoolTaskExecutor workerExecutor(WorkerPollingProperties polling) {
            var worker = new ThreadPoolTaskExecutor();
            worker.setThreadNamePrefix("automation-worker-task-");
            worker.setCorePoolSize(polling.concurrency());
            worker.setMaxPoolSize(polling.concurrency());
            worker.setQueueCapacity(0);
            return worker;
        }

        @Bean("automationFixtureLookup")
        AutomationWorkerHandlerRegistration readOnlyLookup(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings) {
            return lookupRegistration(settings, 1);
        }

        @Bean("automationFixtureLookupV2")
        AutomationWorkerHandlerRegistration readOnlyLookupV2(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings) {
            return lookupRegistration(settings, 2);
        }

        @Bean("automationFixtureTransactionalWrite")
        AutomationWorkerHandlerRegistration transactionalWrite(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings) {
            return new AutomationWorkerHandlerRegistration(descriptor("fixtureTransaction", 1, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, null),
                    INPUT, OUTPUT, (context, jdbc) -> persistEffect(settings, context, jdbc), null);
        }

        @Bean("automationFixtureExternalWrite")
        AutomationWorkerHandlerRegistration externalWrite(@Qualifier("automationWorkerFixtureSettings") FixtureSettingsVO settings,
                @Qualifier("automationWorkerHttp") HttpClient http) {
            return new AutomationWorkerHandlerRegistration(descriptor(EXTERNAL, 1, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, EXTERNAL_TARGET),
                    INPUT, OUTPUT, null, (context, effectKey) -> dispatch(settings, context, http));
        }
    }

    private static AutomationWorkerHandlerRegistration lookupRegistration(FixtureSettingsVO settings, long version) {
        return new AutomationWorkerHandlerRegistration(descriptor("fixtureLookup", version, WorkerEffectSafetyEnum.READ_ONLY, "fixture-lookup"),
                INPUT, OUTPUT, null, (context, effectKey) -> {
                    requireContext(settings, context);
                    if (TransactionSynchronizationManager.isActualTransactionActive()) throw uncertain();
                    boolean found = LOOKUP.getOrDefault(context.input().getOrDefault("value", "fixture"), false);
                    return new AutomationWorkerBusinessResultDTO(receiptId(settings, context), Map.of("accepted", found));
                });
    }

    private static AutomationWorkerHandlerDescriptor descriptor(String key, long version, WorkerEffectSafetyEnum mode, String target) {
        return new AutomationWorkerHandlerDescriptor(key, version, INPUT_SCHEMA_SHA256, OUTPUT_SCHEMA_SHA256, mode, HANDLER_VERSION, target);
    }

    private static AutomationWorkerBusinessResultDTO persistEffect(FixtureSettingsVO settings,
            AutomationWorkerExecutionContextVO context, JdbcOperations jdbc) {
        requireContext(settings, context);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw uncertain();
        String inputHash = inputHash(context);
        String receipt = receiptId(settings, context);
        jdbc.update("""
                INSERT INTO automation_fixture.business_effect (run_id,tenant_id,effect_key,input_sha256,result_ref)
                VALUES (?,?,?,?,?) ON CONFLICT (run_id,tenant_id,effect_key) DO NOTHING
                """, settings.runId(), context.tenantId(), context.effectKey(), inputHash, receipt);
        Boolean matches = jdbc.queryForObject("""
                SELECT input_sha256=? AND result_ref=? FROM automation_fixture.business_effect
                WHERE run_id=? AND tenant_id=? AND effect_key=?
                """, Boolean.class, inputHash, receipt, settings.runId(), context.tenantId(), context.effectKey());
        if (!Boolean.TRUE.equals(matches)) throw uncertain();
        return new AutomationWorkerBusinessResultDTO(receipt, ACCEPTED);
    }

    private static AutomationWorkerBusinessResultDTO dispatch(FixtureSettingsVO settings,
            AutomationWorkerExecutionContextVO context, HttpClient http) {
        requireContext(settings, context);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw uncertain();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("effectKey", context.effectKey());
        payload.put("generation", context.dispatchGeneration());
        payload.put("tenantId", context.tenantId());
        payload.put("inputSha256", inputHash(context));
        if (context.input().get("waitToken") instanceof String token) {
            if (!token.matches("[A-Za-z0-9_-]{43}")) throw uncertain();
            payload.put("waitToken", token);
        }
        Duration remaining = Duration.between(Instant.now(), context.deadlineAt());
        if (remaining.isNegative() || remaining.isZero()) throw uncertain();
        Duration timeout = remaining.compareTo(WORK_TIMEOUT) < 0 ? remaining : WORK_TIMEOUT;
        var request = HttpRequest.newBuilder(settings.external().resolve("/dispatch")).timeout(timeout)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(ProcessTypedJson.canonicalBytes(payload, MAX_TARGET_BODY_BYTES))).build();
        var pending = http.sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_TARGET_BODY_BYTES));
        try {
            var response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200 || !request.uri().equals(response.uri())) throw uncertain();
            var body = JSON.readTree(response.body());
            var receipt = body.path("businessReceiptId");
            var output = body.path("output");
            if (!body.isObject() || body.size() != 2 || !receipt.isString() || !output.isObject() || output.size() != 1
                    || !output.path("accepted").isBoolean() || !output.path("accepted").asBoolean()) throw uncertain();
            var result = new AutomationWorkerBusinessResultDTO(receipt.asString(), ACCEPTED);
            if (!result.isBusinessResultValid()) throw uncertain();
            return result;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw uncertain();
        } catch (ExecutionException | TimeoutException | tools.jackson.core.JacksonException failure) {
            throw uncertain();
        } finally {
            pending.cancel(true);
        }
    }

    private static WorkerEffectEvidenceVO observe(FixtureSettingsVO settings, JdbcTemplate jdbc, WorkerEffectReconcileQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || !SOURCE_ID.equals(query.getSourceId())
                || query.getTenantId() != settings.tenantId() || !EXTERNAL.equals(query.getCapabilityKey())
                || query.getCapabilityVersion() != 1 || !HANDLER_VERSION.equals(query.getHandlerVersion())
                || !EXTERNAL_TARGET.equals(query.getTargetId())) throw uncertain();
        var evidence = jdbc.query("""
                SELECT DISTINCT d.applied,d.result_ref,d.input_sha256,e.business_input_sha256,
                       b.input_sha256 AS applied_input,b.result_ref AS applied_ref,statement_timestamp() AS observed_at
                FROM tianji_worker.tianji_worker_effect_receipt e
                JOIN automation_fixture.target_dispatch d ON d.run_id=? AND d.tenant_id=e.tenant_id
                    AND d.effect_key=e.effect_key AND d.dispatch_generation=e.dispatch_generation
                LEFT JOIN automation_fixture.business_effect b ON b.run_id=d.run_id AND b.tenant_id=d.tenant_id AND b.effect_key=d.effect_key
                WHERE e.source_id=? AND e.tenant_id=? AND e.effect_key=? AND e.dispatch_generation=?
                    AND e.effect_fingerprint=? AND e.capability_key=? AND e.capability_version=? AND e.handler_version=? AND e.target_id=?
                LIMIT 2
                """, (rows, index) -> new TargetObservationVO(rows.getBoolean("applied"), rows.getString("result_ref"),
                    rows.getString("input_sha256"), rows.getString("business_input_sha256"), rows.getString("applied_input"),
                    rows.getString("applied_ref"), rows.getObject("observed_at", OffsetDateTime.class).toInstant()),
                    settings.runId(), SOURCE_ID, query.getTenantId(), query.getEffectKey(), query.getDispatchGeneration(),
                    query.getEffectFingerprint(), EXTERNAL, query.getCapabilityVersion(), HANDLER_VERSION, EXTERNAL_TARGET);
        if (evidence.size() != 1) throw uncertain();
        var row = evidence.getFirst();
        if (!row.inputHash().equals(row.workerInputHash()) || (row.applied()
                ? row.receipt() == null || !row.receipt().equals(row.appliedRef()) || !row.inputHash().equals(row.appliedInput())
                : row.receipt() != null || row.appliedRef() != null || row.appliedInput() != null)) throw uncertain();
        String digest = ProcessTypedJson.sha256("AUTOMATION_FIXTURE_EFFECT_EVIDENCE_V1", ProcessTypedJson.canonicalBytes(
                Arrays.asList(settings.runId(), query.getTenantId(), query.getEffectKey(), query.getDispatchGeneration(),
                        row.inputHash(), row.applied(), row.receipt())));
        String resultHash = row.applied()
                ? AutomationWorkerBusinessResultDTO.resultSha256(query.getEffectFingerprint(), row.receipt(), ACCEPTED) : null;
        return new WorkerEffectEvidenceVO(row.applied() ? WorkerEffectEvidenceVO.Status.APPLIED : WorkerEffectEvidenceVO.Status.NOT_APPLIED_FINAL,
                query.getEffectKey(), query.getEffectFingerprint(), EXTERNAL_TARGET, query.getDispatchGeneration(), AUTHORITY,
                row.observedAt(), row.observedAt().plusSeconds(30), digest, row.receipt(), row.applied() ? ACCEPTED : null, resultHash);
    }

    private static void requireContext(FixtureSettingsVO settings, AutomationWorkerExecutionContextVO context) {
        if (context.tenantId() != settings.tenantId() || !SOURCE_ID.equals(context.sourceId())
                || !Instant.now().isBefore(context.deadlineAt())) throw uncertain();
    }

    private static String inputHash(AutomationWorkerExecutionContextVO context) {
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_BUSINESS_INPUT_V2", ProcessTypedJson.canonicalBytes(context.input(), 65_536));
    }

    private static String receiptId(FixtureSettingsVO settings, AutomationWorkerExecutionContextVO context) {
        return "fixture-" + ProcessTypedJson.sha256("AUTOMATION_FIXTURE_EFFECT_RECEIPT_V1",
                ProcessTypedJson.canonicalBytes(List.of(settings.runId(), context.tenantId(), context.effectKey())));
    }

    private static WorkerHandlerFailureException uncertain() {
        return new WorkerHandlerFailureException(WorkerFailureKindEnum.UNKNOWN, "WORKER_FIXTURE_OUTCOME_UNKNOWN");
    }

    private static FixtureSettingsVO settings(String[] args) throws Exception {
        if (args.length != 1 || !args[0].startsWith(LOCATION)) throw unavailable("WORKER_FIXTURE_SETTINGS_INVALID");
        Path root = Path.of("").toRealPath();
        privateDirectory(root, root);
        Path config = Path.of(URI.create(args[0].substring(LOCATION.length())));
        var properties = new Properties() {
            @Override public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw unavailable("WORKER_FIXTURE_DUPLICATE_PROPERTY");
                return super.put(key, value);
            }
        };
        byte[] configBytes = privateBytes(root, config, 65_536);
        try (var input = new ByteArrayInputStream(configBytes)) {
            properties.load(input);
        } finally {
            Arrays.fill(configBytes, (byte) 0);
        }
        for (String key : properties.stringPropertyNames()) {
            if (!(key.startsWith(FIXTURE) && FIXTURE_FIELDS.contains(key.substring(FIXTURE.length())))
                    && !(key.startsWith(POLLING) && POLLING_FIELDS.contains(key.substring(POLLING.length())))
                    && !HOST_FIELDS.contains(key)) throw unavailable("WORKER_FIXTURE_UNKNOWN_PROPERTY");
        }
        if (!"true".equals(required(properties, FIXTURE + "run-authorized")) || !"WORKER".equals(required(properties, FIXTURE + "role"))
                || !"true".equals(required(properties, POLLING + "enabled"))
                || !"false".equals(properties.getProperty("spring.flyway.enabled", "false"))
                || !"never".equals(properties.getProperty("spring.sql.init.mode", "never"))) throw unavailable("WORKER_FIXTURE_SETTINGS_INVALID");
        String runId = required(properties, FIXTURE + "run-id");
        String generation = required(properties, FIXTURE + "generation");
        String candidate = required(properties, FIXTURE + "candidate-sha256");
        String jdbcUrl = required(properties, "spring.datasource.url");
        String username = required(properties, "spring.datasource.username");
        var database = JDBC.matcher(jdbcUrl);
        if (!runId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || !generation.matches("[1-9][0-9]{0,8}")
                || !candidate.matches("[0-9a-f]{64}") || !database.matches() || Integer.parseInt(database.group(1)) > 65_535
                || !("tianji_it_automation_w_runtime_" + database.group(3)).equals(username)) throw unavailable("WORKER_FIXTURE_SETTINGS_INVALID");
        String tenant = required(properties, POLLING + "tenant-id");
        if (!tenant.matches("[1-9][0-9]{0,18}")) throw unavailable("WORKER_FIXTURE_SETTINGS_INVALID");
        Path ready = Path.of(required(properties, FIXTURE + "ready-file"));
        if (!ready.isAbsolute() || !ready.equals(ready.normalize()) || ready.getFileName() == null || ready.getParent() == null
                || Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) throw unavailable("WORKER_FIXTURE_READY_PATH_INVALID");
        privateDirectory(root, ready.getParent());
        privateDirectory(root, Path.of(required(properties, FIXTURE + "gate-directory")));
        Path acquire = Path.of(required(properties, FIXTURE + "worker-acquire-token-file"));
        Path report = Path.of(required(properties, FIXTURE + "worker-report-token-file"));
        readToken(root, acquire);
        readToken(root, report);
        var polling = new LinkedHashMap<String, Object>();
        for (String key : POLLING_FIELDS) {
            String value = properties.getProperty(POLLING + key);
            if (value != null) polling.put(POLLING + key, value);
        }
        polling.put("spring.config.name", "automation-worker-acceptance");
        polling.put("spring.main.banner-mode", "off");
        return new FixtureSettingsVO(root, runId, generation, candidate, ready, Long.parseLong(tenant), jdbcUrl, username,
                required(properties, "spring.datasource.password"), httpsRoot(required(properties, FIXTURE + "process-relay")),
                httpsRoot(required(properties, FIXTURE + "external-target")), acquire, report, trust(root, properties), Map.copyOf(polling));
    }

    private static SSLContext trust(Path root, Properties properties) throws Exception {
        Path file = Path.of(required(properties, FIXTURE + "trust-store"));
        Path passwordFile = Path.of(required(properties, FIXTURE + "trust-password-file"));
        byte[] passwordBytes = privateBytes(root, passwordFile, 1_024);
        var decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(passwordBytes));
        int length = decoded.remaining();
        if (length > 0 && decoded.get(length - 1) == '\n') length--;
        var password = new char[length];
        decoded.get(password);
        try (var input = new ByteArrayInputStream(privateBytes(root, file, 1_048_576))) {
            var store = KeyStore.getInstance("PKCS12");
            store.load(input, password);
            var aliases = Collections.list(store.aliases());
            if (aliases.isEmpty()) throw unavailable("WORKER_FIXTURE_TRUST_INVALID");
            for (String alias : aliases) {
                if (!store.isCertificateEntry(alias)) throw unavailable("WORKER_FIXTURE_TRUST_INVALID");
            }
            var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            var tls = SSLContext.getInstance("TLS");
            tls.init(null, factory.getTrustManagers(), null);
            return tls;
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
            Arrays.fill(password, '\0');
        }
    }

    private static URI httpsRoot(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1 || uri.getPort() > 65_535
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("".equals(uri.getRawPath()) || "/".equals(uri.getRawPath()))) throw unavailable("WORKER_FIXTURE_ENDPOINT_INVALID");
        return uri;
    }

    private static String token(FixtureSettingsVO settings, Path file) {
        try {
            return readToken(settings.root(), file);
        } catch (IOException failure) {
            throw unavailable("WORKER_FIXTURE_TOKEN_UNAVAILABLE");
        }
    }

    private static String readToken(Path root, Path file) throws IOException {
        byte[] bytes = privateBytes(root, file, 16_384);
        try {
            String token = new String(bytes, StandardCharsets.US_ASCII).strip();
            if (!token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) throw unavailable("WORKER_FIXTURE_TOKEN_INVALID");
            return token;
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static byte[] privateBytes(Path root, Path file, int limit) throws IOException {
        if (!file.isAbsolute() || !file.equals(file.normalize()) || file.getParent() == null) throw unavailable("WORKER_FIXTURE_PATH_INVALID");
        privateDirectory(root, file.getParent());
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.getOwner(root).equals(Files.getOwner(file, LinkOption.NOFOLLOW_LINKS))
                || !Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))) {
            throw unavailable("WORKER_FIXTURE_FILE_NOT_PRIVATE");
        }
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length < 1 || bytes.length > limit) throw unavailable("WORKER_FIXTURE_FILE_SIZE_INVALID");
            return bytes;
        }
    }

    private static void privateDirectory(Path root, Path directory) throws IOException {
        if (!directory.isAbsolute() || !directory.equals(directory.normalize()) || !directory.startsWith(root)
                || !directory.equals(directory.toRealPath())) throw unavailable("WORKER_FIXTURE_PATH_INVALID");
        var owner = root.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        for (Path path = directory; path.startsWith(root); path = path.getParent()) {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || !owner.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
                    || !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"))) {
                throw unavailable("WORKER_FIXTURE_DIRECTORY_NOT_PRIVATE");
            }
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank() || value.length() > 4_096 || value.chars().anyMatch(Character::isISOControl)) {
            throw unavailable("WORKER_FIXTURE_SETTINGS_INVALID");
        }
        return value;
    }

    private static void publishReady(FixtureSettingsVO settings) throws IOException {
        var file = Files.createTempFile(settings.ready().getParent(), ".worker-ready-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(file, "runId=" + settings.runId() + "\nrole=WORKER\ngeneration=" + settings.generation()
                    + "\npid=" + ProcessHandle.current().pid() + "\nport=0\ncandidateSha256=" + settings.candidateSha256() + "\n");
            if (Files.exists(settings.ready(), LinkOption.NOFOLLOW_LINKS)) throw unavailable("WORKER_FIXTURE_READY_PATH_INVALID");
            Files.move(file, settings.ready(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static IllegalStateException unavailable(String code) {
        return new IllegalStateException(code);
    }

    private record FixtureSettingsVO(Path root, String runId, String generation, String candidateSha256, Path ready, long tenantId,
            String jdbcUrl, String username, String password, URI process, URI external, Path acquireToken, Path reportToken,
            SSLContext trust, Map<String, Object> polling) {
        @Override public String toString() { return "FixtureSettingsVO[role=WORKER]"; }
    }

    private record TargetObservationVO(boolean applied, String receipt, String inputHash, String workerInputHash,
            String appliedInput, String appliedRef, Instant observedAt) { }
}
