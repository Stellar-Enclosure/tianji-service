package top.egon.cola.platform.tianji.agent.server.acceptance;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.context.support.StandardServletEnvironment;
import reactor.core.publisher.Flux;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.component.agentflow.adapter.adk.AdkAgentExecutorAdapter;
import top.egon.cola.component.agentflow.adapter.adk.AdkAgentExecutorAdapter.LeafDecisionVO;
import top.egon.cola.component.agentflow.adapter.adk.AdkAgentExecutorAdapter.RegisteredLeafVO;
import top.egon.cola.component.agentflow.adapter.adk.AdkAgentExecutorAdapter.RegisteredTemplateVO;
import top.egon.cola.component.agentflow.adapter.springai.SpringAiExecutionModelAdapter.ModelRegistrationVO;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO.DependencyRefVO;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO.LeafVO;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO.MergeEnum;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO.RootTerminalVO;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO.StrategyEnum;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO.TerminationModeEnum;
import top.egon.cola.platform.tianji.agent.application.run.AgentRuntimeExecutionService;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.agent.server.integration.process.OutboxAgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.agent.server.security.TianjiGrantDispatchPoller;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.math.BigDecimal;
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
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

/** Explicit Agent Server JVM for the scoped automation acceptance fixture. */
@Slf4j
public final class AutomationAgentAcceptanceMain {
    private static final String PREFIX = "tianji.automation.fixture.";
    private static final String ROLE = "AGENT";
    private static final String MODEL = "automationFixtureModel";
    private static final String INSTRUCTION = "Classify the bounded automation fixture input.";
    private static final DependencyRefVO INSTRUCTION_REF = new DependencyRefVO("automationFixtureInstruction", 1,
            "d36ec2a0e72a1e7c771b5f19f6442cc7f6f17c050ac2bd9479ef29c06db4d87a");
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final String CONFIG_ARGUMENT = "--spring.config.additional-location=";
    private static final int MAX_SETTINGS_BYTES = 128 * 1024;
    private static final int MAX_CATALOG_BYTES = 256 * 1024;
    private static final int MAX_MODEL_INPUT_BYTES = 16 * 1024;
    private static final int MAX_OUTPUT_TOKENS = 64;
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final Set<String> FIXTURE_KEYS = Set.of("run-authorized", "run-id", "role", "generation", "ready-file",
            "gate-directory", "candidate-sha256", "trust-store", "trust-password-file", "template-catalog");
    private static final Set<String> BOOT_KEYS = Set.of("spring.main.web-application-type", "server.address", "server.port",
            "server.ssl.enabled", "server.ssl.key-store", "server.ssl.key-store-type", "server.ssl.key-store-password",
            "server.ssl.key-password", "server.ssl.key-alias", "spring.datasource.url", "spring.datasource.username",
            "spring.datasource.password", "spring.datasource.hikari.schema", "spring.flyway.enabled", "spring.sql.init.mode",
            "spring.autoconfigure.exclude", "egon.cola.component.id.machine-id");
    private static final List<String> PRODUCT_PREFIXES = List.of("stellar.tianji.agent.",
            "egon.cola.component.agent-flow.", "egon.cola.component.agent-flow-registry.",
            "egon.cola.component.transactional-outbox.", "egon.cola.platform.tianquan.shoubing.",
            "egon.cola.platform.tianquan.jianshen.", "spring.security.oauth2.client.");

    private AutomationAgentAcceptanceMain() { }

    public static void main(String[] args) {
        try {
            var settings = privateSettings(args);
            installTrust(settings);
            var environment = new StandardServletEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().addFirst(new MapPropertySource("automationAcceptanceAgent", settings.boot()));
            var context = new SpringApplicationBuilder(TianjiAgentServerApplication.class, FixtureConfiguration.class)
                    .web(WebApplicationType.SERVLET)
                    .environment(environment)
                    .profiles("tianji-automation-acceptance-fixture")
                    .initializers(application -> {
                        var registry = (GenericApplicationContext) application;
                        registry.registerBean("automationFixtureAgentSettings", AgentSettingsVO.class, () -> settings);
                        registry.registerBean("tianjiAgentDispatchScheduler", ThreadPoolTaskScheduler.class,
                                () -> dispatchScheduler(settings.gates().resolve("agent-before-claim")));
                    })
                    .run("--spring.config.location=classpath:/application.yml", "--spring.main.web-application-type=servlet");
            try {
                writeReady(settings, (ServletWebServerApplicationContext) context);
            } catch (Exception failure) {
                context.close();
                throw failure;
            }
        } catch (Exception failure) {
            String known = failure.getMessage();
            String reason = known != null && known.matches("TIANJI_AUTOMATION_FIXTURE_[A-Z_]+")
                    ? known : failure.getClass().getSimpleName();
            log.error("TIANJI_AUTOMATION_AGENT_STARTUP_FAILED reason={}", reason);
            throw invalid("AGENT_STARTUP");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("tianji-automation-acceptance-fixture")
    static class FixtureConfiguration {
        @Bean("automationFixtureChatModel")
        ChatModel automationFixtureChatModel(@Qualifier("automationFixtureAgentSettings") AgentSettingsVO settings) {
            return new FixtureChatModel(settings.gates());
        }

        @Bean("automationFixtureTemplateCatalog")
        TemplateCatalogVO automationFixtureTemplateCatalog(
                @Qualifier("automationFixtureAgentSettings") AgentSettingsVO settings,
                @Qualifier("automationFixtureChatModel") ChatModel model,
                @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers) throws IOException {
            var raw = JSON.readValue(readBytes(settings.catalog(), MAX_CATALOG_BYTES), OBJECT);
            if (!raw.keySet().equals(Set.of("templates")) || !(raw.get("templates") instanceof List<?> rows)
                    || rows.isEmpty() || rows.size() > 32) throw invalid("AGENT_CATALOG");
            var registration = new ModelRegistrationVO(MODEL, MODEL, model, MAX_MODEL_INPUT_BYTES,
                    MAX_OUTPUT_TOKENS, AutomationAgentAcceptanceMain::estimatePrompt);
            var templates = rows.stream().map(row -> template(object(row), registration, issuers))
                    .collect(Collectors.toUnmodifiableMap(PinnedTemplateVO::key, entry -> entry));
            return new TemplateCatalogVO(templates);
        }

        @Bean("agentExecutionTemplateResolver")
        AdkAgentExecutorAdapter.TemplateResolver agentExecutionTemplateResolver(
                @Qualifier("automationFixtureTemplateCatalog") TemplateCatalogVO catalog) {
            return (expected, scope) -> {
                if (!"tianji-agent-runtime".equals(scope.namespace()) || !"EXECUTE".equals(scope.purpose())
                        || !AgentRuntimeExecutionService.ADK_EXECUTOR_REF.equals(expected.executorRef())) return null;
                var pinned = catalog.templates().get(new TemplateKeyVO(scope.tenantKey(), expected.key(), expected.version()));
                return pinned != null && pinned.template().contentSha256().equals(expected.contentSha256()) ? pinned.template() : null;
            };
        }

        @Bean("tianjiAgentRuntimeTemplatePublicationAuthority")
        AgentLedgerService.RuntimeTemplatePublicationAuthority tianjiAgentRuntimeTemplatePublicationAuthority(
                @Qualifier("automationFixtureTemplateCatalog") TemplateCatalogVO catalog,
                @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers) {
            return (publisher, command) -> {
                var key = new TemplateKeyVO(Long.toString(publisher.tenantId()), command.getTemplateKey(), command.getTemplateVersion());
                var pinned = catalog.templates().get(key);
                if (pinned == null || !pinned.publisher().equals(publisher)
                        || !pinned.template().contentSha256().equals(command.contentSha256())
                        || !currentIssuer(issuers, pinned.issuer(), key.tenant())) return null;
                var observedAt = Instant.now();
                return databaseNow -> !databaseNow.isBefore(observedAt.minusSeconds(1))
                        && databaseNow.isBefore(observedAt.plusSeconds(30))
                        && currentIssuer(issuers, pinned.issuer(), key.tenant());
            };
        }

        @Bean("automationFixtureModelBudgetEstimator")
        AgentModelBudgetEstimator automationFixtureModelBudgetEstimator() {
            return new AgentModelBudgetEstimator() {
                @Override
                public Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request) {
                    requireModel(request);
                    int bytes = request.contents().stream().mapToInt(content -> content.parts().orElse(List.of()).stream()
                            .mapToInt(part -> part.text().orElseThrow(() -> invalid("AGENT_MODEL_INPUT"))
                                    .getBytes(StandardCharsets.UTF_8).length).sum()).sum();
                    if (bytes < 1 || bytes > MAX_MODEL_INPUT_BYTES) throw invalid("AGENT_MODEL_INPUT");
                    return new Estimate(bytes + 128L, MAX_OUTPUT_TOKENS, BigDecimal.ZERO);
                }

                @Override
                public ContextWindow contextWindow(AgentExecutionSnapshot snapshot, LlmRequest request) {
                    requireModel(request);
                    return new ContextWindow(MODEL, snapshot.definition().dependencies().modelRouteRef(),
                            snapshot.definition().dependencies().policyVersionRef(), MAX_MODEL_INPUT_BYTES, 512);
                }

                @Override
                public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response) {
                    var usage = response.usageMetadata().orElseThrow(() -> invalid("AGENT_MODEL_USAGE"));
                    if (usage.promptTokenCount().orElse(-1) != 10 || usage.candidatesTokenCount().orElse(-1) != 5) {
                        throw invalid("AGENT_MODEL_USAGE");
                    }
                    return new AgentBudgetSettlement(10L, 5L, BigDecimal.ZERO);
                }
            };
        }
    }

    private static PinnedTemplateVO template(Map<String, Object> row, ModelRegistrationVO model, TrustedIssuerRegistry issuers) {
        if (!row.keySet().equals(Set.of("tenantId", "issuer", "publisherSubject", "command"))
                || !(row.get("issuer") instanceof String issuer)
                || !(row.get("publisherSubject") instanceof String publisherSubject)) throw invalid("AGENT_CATALOG");
        long tenantId = integer(row.get("tenantId"));
        var tenant = Long.toString(tenantId);
        loopbackHttps(issuer);
        if (!currentIssuer(issuers, issuer, tenant)) throw invalid("AGENT_CATALOG_ISSUER");
        var publisher = TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.SERVICE, publisherSubject, issuer);
        var command = AgentRuntimeTemplateCommand.fromExactWire(object(row.get("command")));
        var config = command.getConfiguration();
        if (!AgentRuntimeExecutionService.ADK_EXECUTOR_REF.equals(command.getExecutorRef()) || !MODEL.equals(config.modelRef())
                || !INSTRUCTION_REF.key().equals(config.instructionRef().key()) || INSTRUCTION_REF.version() != config.instructionRef().version()
                || !INSTRUCTION_REF.contentSha256().equals(config.instructionRef().contentSha256()) || !config.toolRefs().isEmpty()
                || config.agenticPlan() != null) {
            throw invalid("AGENT_CATALOG_TEMPLATE");
        }
        var leaf = new LeafVO("fixture", INSTRUCTION_REF, MODEL, List.of());
        var plan = new AgenticPlanVO(StrategyEnum.REASONING, List.of(leaf), List.of(), null, 1, leaf.agentKey(),
                MergeEnum.OUTPUT_AGENT, List.of(), new RootTerminalVO(TerminationModeEnum.ROOT_TERMINAL));
        var registered = new RegisteredLeafVO(INSTRUCTION_REF, INSTRUCTION, MODEL, model, List.of(),
                (invocation, key, round) -> new LeafDecisionVO(true, null));
        var template = new RegisteredTemplateVO(command.getTemplateKey(), command.getTemplateVersion(), command.contentSha256(),
                plan, Map.of(leaf.agentKey(), registered), Map.of());
        return new PinnedTemplateVO(new TemplateKeyVO(tenant, template.key(), template.version()), issuer, publisher, template);
    }

    private static boolean currentIssuer(TrustedIssuerRegistry issuers, String issuer, String tenant) {
        return issuers.findByIssuer(issuer).filter(trust -> trust.status() == TrustedIssuerRegistry.Status.ACTIVE
                && trust.realmType() == TrustedIssuerRegistry.RealmType.TENANT && tenant.equals(trust.tenantId())).isPresent();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw invalid("AGENT_FIXTURE_OBJECT");
        }
        return (Map<String, Object>) map;
    }

    private static long integer(Object value) {
        return switch (value) {
            case Integer exact -> exact.longValue();
            case Long exact -> exact;
            default -> throw invalid("AGENT_FIXTURE_INTEGER");
        };
    }

    private static void requireModel(LlmRequest request) {
        if (!MODEL.equals(request.model().orElse(null)) || request.tools() != null && !request.tools().isEmpty()) {
            throw invalid("AGENT_MODEL_REQUEST");
        }
    }

    private static int estimatePrompt(Prompt prompt) {
        return prompt.getInstructions().stream().mapToInt(message -> message.getText() == null ? 0
                : message.getText().getBytes(StandardCharsets.UTF_8).length).sum() + 128;
    }

    private record FixtureChatModel(Path gates) implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            throw invalid("AGENT_MODEL_STREAM_REQUIRED");
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder().model(MODEL).maxTokens(MAX_OUTPUT_TOKENS).build();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                if (!MODEL.equals(prompt.getOptions().getModel()) || estimatePrompt(prompt) > MAX_MODEL_INPUT_BYTES) {
                    throw invalid("AGENT_MODEL_REQUEST");
                }
                var users = prompt.getInstructions().stream().filter(UserMessage.class::isInstance).toList();
                if (users.size() != 1) throw invalid("AGENT_MODEL_INPUT");
                var envelope = JSON.readValue(users.getFirst().getText(), OBJECT);
                if (!envelope.keySet().equals(Set.of("input", "parameters", "shared"))) throw invalid("AGENT_MODEL_INPUT");
                var input = object(envelope.get("input"));
                var parameters = object(envelope.get("parameters"));
                object(envelope.get("shared"));
                if (!(input.get("scenario") instanceof String scenario)) throw invalid("AGENT_MODEL_SCENARIO");
                String output = switch (scenario) {
                    case "automatic" -> "{\"route\":\"automatic\"}";
                    case "manual" -> "{\"route\":\"manual\"}";
                    case "invalid-schema" -> "{\"route\":42}";
                    case "pipeline" -> pipeline(input, parameters);
                    default -> throw invalid("AGENT_MODEL_SCENARIO");
                };
                var digest = ProcessTypedJson.sha256("tianji-automation-fixture-model-input-v1", ProcessTypedJson.canonicalBytes(envelope));
                observe(gates.resolve("agent-model-calls"), digest);
                var gate = gates.resolve("agent-before-model-response");
                var trailing = Flux.defer(() -> Files.notExists(gate, LinkOption.NOFOLLOW_LINKS)
                        ? Flux.just(frame(output.substring(1), true))
                        : Flux.interval(Duration.ofMillis(25)).filter(tick -> Files.notExists(gate, LinkOption.NOFOLLOW_LINKS))
                                .next().timeout(Duration.ofSeconds(30)).map(tick -> frame(output.substring(1), true)).flux());
                return Flux.concat(Flux.just(frame(output.substring(0, 1), false)), trailing)
                        .doOnCancel(() -> observe(gates.resolve("agent-model-cancellations"), digest));
            });
        }

        private static String pipeline(Map<String, Object> input, Map<String, Object> parameters) {
            long stage = integer(input.get("stage"));
            long increment = parameters.containsKey("increment") ? integer(parameters.get("increment")) : 1;
            if (stage < 0 || stage > 39 || increment < 1 || increment > 8) throw invalid("AGENT_MODEL_STAGE");
            long nextStage = stage + increment;
            if (nextStage > 39) throw invalid("AGENT_MODEL_STAGE");
            return "{\"stage\":" + nextStage + "}";
        }

        private static ChatResponse frame(String text, boolean terminal) {
            return terminal ? new ChatResponse(List.of(new Generation(new AssistantMessage(text),
                    ChatGenerationMetadata.builder().finishReason("stop").build())),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5, 15)).build())
                    : new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }

        private static synchronized void observe(Path file, String digest) {
            try {
                if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createFile(file, PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !PRIVATE_FILE.equals(Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS))) {
                    throw invalid("AGENT_MODEL_EVIDENCE");
                }
                Files.writeString(file, Instant.now() + "," + digest + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException failure) {
                throw invalid("AGENT_MODEL_EVIDENCE");
            }
        }
    }

    private static ThreadPoolTaskScheduler dispatchScheduler(Path gate) {
        var scheduler = new ThreadPoolTaskScheduler() {
            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
                return super.scheduleWithFixedDelay(() -> {
                    if (Files.notExists(gate, LinkOption.NOFOLLOW_LINKS)) task.run();
                }, delay);
            }
        };
        scheduler.setPoolSize(1);
        scheduler.setThreadFactory(Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false)
                .name("tianji-automation-agent-discovery-", 0).factory());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setPhase(Integer.MAX_VALUE - 300);
        return scheduler;
    }

    private static AgentSettingsVO privateSettings(String[] args) throws IOException {
        if (args == null || args.length != 1 || !args[0].startsWith(CONFIG_ARGUMENT)
                || !"false".equals(System.getenv("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS"))) throw invalid("AGENT_SETTINGS");
        var root = Path.of("").toRealPath();
        privateDirectory(root, root);
        var file = privateFile(root, Path.of(URI.create(args[0].substring(CONFIG_ARGUMENT.length()))), MAX_SETTINGS_BYTES);
        var input = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw invalid("AGENT_SETTINGS_DUPLICATE");
                return super.put(key, value);
            }
        };
        byte[] bytes = readBytes(file, MAX_SETTINGS_BYTES);
        try (var stream = new java.io.ByteArrayInputStream(bytes)) {
            input.load(stream);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
        Map<String, Object> boot = input.stringPropertyNames().stream().filter(key -> !key.startsWith(PREFIX))
                .collect(Collectors.toUnmodifiableMap(key -> key, input::getProperty));
        input.stringPropertyNames().stream().filter(key -> key.startsWith(PREFIX)).forEach(key -> {
            if (!FIXTURE_KEYS.contains(key.substring(PREFIX.length()))) throw invalid("AGENT_SETTINGS_KEY");
        });
        boot.forEach(AutomationAgentAcceptanceMain::validateProperty);
        requireSetting(input, PREFIX + "run-authorized", "true");
        requireSetting(input, PREFIX + "role", ROLE);
        requireSetting(input, "server.address", "127.0.0.1");
        requireSetting(input, "server.port", "0");
        requireSetting(input, "server.ssl.enabled", "true");
        requireSetting(input, "server.ssl.key-store-type", "PKCS12");
        requireSetting(input, "spring.flyway.enabled", "false");
        requireSetting(input, "spring.sql.init.mode", "never");
        requireSetting(input, "spring.datasource.hikari.schema", "tianji_agent");
        for (String feature : List.of("storage", "api", "runtime", "runtime-v2", "dispatch", "reconciliation", "execution-grants",
                "execution-authorization", "linked", "service-execution", "terminal-events", "result-delivery")) {
            requireSetting(input, "stellar.tianji.agent." + feature + ".enabled", "true");
        }
        requireSetting(input, "egon.cola.component.agent-flow.enabled", "true");
        requireSetting(input, "egon.cola.component.agent-flow-registry.enabled", "false");
        var jdbc = value(input, "spring.datasource.url");
        if (!jdbc.startsWith("jdbc:")) throw invalid("AGENT_SETTINGS_STORE");
        var url = URI.create(jdbc.substring(5));
        if (!"postgresql".equals(url.getScheme()) || !"127.0.0.1".equals(url.getHost()) || url.getPort() < 1 || url.getPort() > 65535
                || url.getUserInfo() != null || url.getFragment() != null || !url.getPath().matches("/tianji_it_automation_as_[a-z0-9_]{1,35}")
                || url.getQuery() != null && !url.getQuery().matches("(?:currentSchema=tianji_agent|sslmode=disable)(?:&(?:currentSchema=tianji_agent|sslmode=disable))?")) {
            throw invalid("AGENT_SETTINGS_STORE");
        }
        privateFile(root, Path.of(URI.create(value(input, "server.ssl.key-store"))), 1024 * 1024);
        var run = value(input, PREFIX + "run-id");
        var generation = value(input, PREFIX + "generation");
        var candidate = value(input, PREFIX + "candidate-sha256");
        if (!run.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || !generation.matches("[1-9][0-9]{0,5}")
                || !candidate.matches("[0-9a-f]{64}")) throw invalid("AGENT_SETTINGS_RUN");
        var ready = Path.of(value(input, PREFIX + "ready-file"));
        privateDirectory(root, ready.getParent());
        if (!ready.isAbsolute() || !ready.equals(ready.normalize()) || !Files.notExists(ready, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("AGENT_SETTINGS_READY");
        }
        var gateDirectory = privateDirectory(root, Path.of(value(input, PREFIX + "gate-directory")));
        var catalog = privateFile(root, Path.of(value(input, PREFIX + "template-catalog")), MAX_CATALOG_BYTES);
        var trust = privateFile(root, Path.of(value(input, PREFIX + "trust-store")), 1024 * 1024);
        var password = privateFile(root, Path.of(value(input, PREFIX + "trust-password-file")), 1024);
        return new AgentSettingsVO(run, generation, candidate, ready, gateDirectory, catalog, trust, password, boot);
    }

    private static void validateProperty(String key, Object raw) {
        boolean health = key.matches("management\\.health\\.[a-z0-9-]+\\.enabled");
        if (!BOOT_KEYS.contains(key) && !health && PRODUCT_PREFIXES.stream().noneMatch(key::startsWith)) {
            throw invalid("AGENT_SETTINGS_KEY");
        }
        var value = (String) raw;
        if (health && !Set.of("true", "false").contains(value)) throw invalid("AGENT_SETTINGS_HEALTH");
        if (value.contains("${") || value.matches(".*@[A-Z_]+@.*") || value.chars().anyMatch(Character::isISOControl)) {
            throw invalid("AGENT_SETTINGS_VALUE");
        }
        String transport = value.toLowerCase(Locale.ROOT);
        if (transport.startsWith("https://") || transport.startsWith("http://")) {
            boolean logicalResource = key.endsWith("resource-uri") || key.endsWith("source-resource") || key.endsWith("audience");
            if (!logicalResource || !value.startsWith("https://")) loopbackHttps(value);
        } else if (transport.startsWith("redis://") || transport.startsWith("rediss://")) {
            throw invalid("AGENT_SETTINGS_REMOTE");
        }
    }

    private static URI loopbackHttps(String text) {
        var uri = URI.create(text);
        if (!"https".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1 || uri.getPort() > 65535
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !uri.equals(uri.normalize())) throw invalid("AGENT_SETTINGS_REMOTE");
        return uri;
    }

    private static String value(Properties source, String key) {
        String found = source.getProperty(key);
        if (found == null || found.isBlank()) throw invalid("AGENT_SETTINGS_REQUIRED");
        return found;
    }

    private static void requireSetting(Properties source, String key, String expected) {
        if (!expected.equals(source.getProperty(key))) throw invalid("AGENT_SETTINGS_BINDING");
    }

    private static Path privateDirectory(Path root, Path directory) throws IOException {
        if (directory == null || !directory.isAbsolute() || !directory.startsWith(root) || !directory.equals(directory.normalize())
                || !directory.equals(directory.toRealPath()) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw invalid("AGENT_PRIVATE_DIRECTORY");
        }
        for (Path next = directory; next.startsWith(root); next = next.getParent()) {
            if (!Files.getOwner(root).equals(Files.getOwner(next, LinkOption.NOFOLLOW_LINKS))
                    || !PRIVATE_DIRECTORY.equals(Files.getPosixFilePermissions(next, LinkOption.NOFOLLOW_LINKS))) {
                throw invalid("AGENT_PRIVATE_DIRECTORY");
            }
            if (next.equals(root)) break;
        }
        return directory;
    }

    private static Path privateFile(Path root, Path file, int limit) throws IOException {
        privateDirectory(root, file.getParent());
        if (!file.isAbsolute() || !file.equals(file.normalize()) || !file.equals(file.toRealPath())
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.getOwner(root).equals(Files.getOwner(file))
                || !PRIVATE_FILE.equals(Files.getPosixFilePermissions(file)) || Files.size(file) < 1 || Files.size(file) > limit) {
            throw invalid("AGENT_PRIVATE_FILE");
        }
        return file;
    }

    private static byte[] readBytes(Path file, int limit) throws IOException {
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length == 0 || bytes.length > limit) throw invalid("AGENT_PRIVATE_SIZE");
            return bytes;
        }
    }

    private static void installTrust(AgentSettingsVO settings) throws Exception {
        byte[] bytes = readBytes(settings.trustPassword(), 1024);
        char[] password = null;
        try {
            var decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            int length = decoded.remaining();
            if (decoded.get(length - 1) == '\n') length--;
            if (length < 1) throw invalid("AGENT_TRUST_PASSWORD");
            password = new char[length];
            for (int i = 0; i < length; i++) {
                password[i] = decoded.get(i);
                if (Character.isISOControl(password[i])) throw invalid("AGENT_TRUST_PASSWORD");
            }
            var trust = KeyStore.getInstance(settings.trustStore().toFile(), password);
            var aliases = java.util.Collections.list(trust.aliases());
            if (aliases.isEmpty()) throw invalid("AGENT_TRUST_CERTIFICATES");
            for (String alias : aliases) if (!trust.isCertificateEntry(alias)) throw invalid("AGENT_TRUST_CERTIFICATES");
            var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trust);
            var ssl = SSLContext.getInstance("TLS");
            ssl.init(null, factory.getTrustManagers(), null);
            SSLContext.setDefault(ssl);
            HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private static void writeReady(AgentSettingsVO settings, ServletWebServerApplicationContext context) throws Exception {
        int port = context.getWebServer().getPort();
        var ledger = context.getBean(AgentLedgerService.class);
        var publisher = context.getBean(OutboxAgentTerminalEventPublisher.class);
        context.getBean(AgentRuntimeExecutionService.class);
        if (port < 1 || !context.getBean(TianjiGrantDispatchPoller.class).isRunning() || !ledger.hasTerminalPublisher(publisher)) {
            throw invalid("AGENT_RUNTIME_READINESS");
        }
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var health = client.send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (health.statusCode() != 200 || !"UP".equals(JSON.readValue(health.body(), OBJECT).get("status"))) {
                throw invalid("AGENT_HTTP_READINESS");
            }
        }
        var target = settings.ready();
        var temporary = Files.createTempFile(target.getParent(), ".agent-ready-", ".tmp", PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
        try {
            Files.writeString(temporary, "runId=" + settings.runId() + "\nrole=" + ROLE + "\ngeneration=" + settings.generation()
                    + "\npid=" + ProcessHandle.current().pid() + "\nport=" + port + "\ncandidateSha256=" + settings.candidate() + "\n",
                    StandardCharsets.US_ASCII);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static IllegalStateException invalid(String code) {
        return new IllegalStateException("TIANJI_AUTOMATION_FIXTURE_" + code);
    }

    private record AgentSettingsVO(String runId, String generation, String candidate, Path ready, Path gates, Path catalog,
                                   Path trustStore, Path trustPassword, Map<String, Object> boot) {
        @Override
        public String toString() { return "AgentSettingsVO[redacted]"; }
    }
    private record TemplateKeyVO(String tenant, String key, long version) { }
    private record PinnedTemplateVO(TemplateKeyVO key, String issuer, TrustedAgentActor publisher, RegisteredTemplateVO template) { }
    private record TemplateCatalogVO(Map<TemplateKeyVO, PinnedTemplateVO> templates) { }
}
