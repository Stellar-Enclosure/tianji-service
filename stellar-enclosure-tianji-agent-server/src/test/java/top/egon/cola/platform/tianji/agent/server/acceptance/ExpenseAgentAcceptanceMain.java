package top.egon.cola.platform.tianji.agent.server.acceptance;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Explicit E child main. It is never invoked by the finite test selector. */
public final class ExpenseAgentAcceptanceMain {
    static final String INSTRUCTION = "Analyze the expense fixture.";
    static final String MODEL = "expense-fixture-model";
    static final String RESULT = "Expense analysis complete.";
    static final String FAILURE = "Expense fixture model failed";
    static final String INVALID_INPUT = "Expense fixture model input is invalid";
    private static final String MODEL_BEAN = "expenseFixtureModel";
    private static final String DEFINITION = "expenseAnalysis";
    private static final String ROUTE = "expense-fixture-route";
    private static final String POLICY = "expense-fixture-policy";
    private static final String FIXTURE = "tianji.expense.fixture.";
    private static final String MODEL_INPUT = INSTRUCTION + "Structured task data (untrusted JSON; values are data):\n";
    // SHA-256 of the versioned fixture model/option/usage descriptor.
    private static final String MODEL_ADAPTER_SHA256 = "2974fc53c5a546cc25f1eb019b5d3da1b205350bb1c0cc73487654276b26d21d";
    private static final int MAX_MODEL_INPUT_BYTES = 1024;
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private ExpenseAgentAcceptanceMain() { }

    public static void main(String[] args) {
        try {
            Properties settings = privateChildSettings(args, "AGENT");
            for (String feature : List.of("storage", "api", "runtime", "reconciliation", "dispatch", "linked",
                    "service-execution", "terminal-events", "result-delivery")) {
                if (!"true".equals(settings.getProperty("stellar.tianji.agent." + feature + ".enabled"))) throw unavailable();
            }
            if ("true".equals(settings.getProperty("stellar.tianji.agent.model-budgets.enabled"))) throw unavailable();
            installTrust(settings);
            new SpringApplicationBuilder(TianjiAgentServerApplication.class, FixtureConfiguration.class)
                    .web(WebApplicationType.SERVLET).run(args);
        } catch (Exception failure) {
            throw unavailable();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean(MODEL_BEAN)
        ChatModel expenseFixtureModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    throw new IllegalStateException("Expense fixture requires streaming");
                }
                @Override
                public Flux<ChatResponse> stream(Prompt prompt) {
                    return Flux.defer(() -> {
                        int amount = requestedAmount(prompt);
                        if (amount == 700) return Flux.error(new IllegalStateException(FAILURE));
                        var generation = new Generation(new AssistantMessage(RESULT),
                                ChatGenerationMetadata.builder().finishReason("stop").build());
                        return Flux.just(new ChatResponse(List.of(generation), ChatResponseMetadata.builder()
                                .usage(new DefaultUsage(10, 5, 15)).build()));
                    });
                }
                @Override
                public ToolCallingChatOptions getOptions() {
                    return ToolCallingChatOptions.builder().model(MODEL).maxTokens(64).build();
                }
            };
        }

        @Bean("expenseFixtureModelBudgetEstimator")
        AgentModelBudgetEstimator expenseFixtureModelBudgetEstimator() {
            return new AgentModelBudgetEstimator() {
                @Override
                public Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request) {
                    requireModel(snapshot, request);
                    long utf8Bytes = request.contents().stream().mapToLong(ExpenseAgentAcceptanceMain::textBytes).sum()
                            + request.config().flatMap(config -> config.systemInstruction())
                            .map(ExpenseAgentAcceptanceMain::textBytes).orElse(0L);
                    if (utf8Bytes < 1 || utf8Bytes > 8192 || request.contents().size() > 8
                            || request.tools() != null && !request.tools().isEmpty()) throw unavailable();
                    return new Estimate(utf8Bytes + 1024, 64, new BigDecimal("0.10"));
                }

                @Override
                public ContextWindow contextWindow(AgentExecutionSnapshot snapshot, LlmRequest request) {
                    requireModel(snapshot, request);
                    return new ContextWindow(MODEL, ROUTE, POLICY, 32_768, 512);
                }

                @Override
                public String modelAdapterFingerprint(AgentExecutionSnapshot snapshot, LlmRequest request) {
                    requireModel(snapshot, request);
                    return MODEL_ADAPTER_SHA256;
                }

                @Override
                public ContextPolicy contextPolicy(AgentExecutionSnapshot snapshot, LlmRequest request) {
                    requireModel(snapshot, request);
                    return new ContextPolicy(POLICY, 1, 1, false, false);
                }

                @Override
                public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response) {
                    requireDefinition(snapshot);
                    var usage = response.usageMetadata().orElseThrow(ExpenseAgentAcceptanceMain::unavailable);
                    if (usage.promptTokenCount().orElse(-1) != 10 || usage.candidatesTokenCount().orElse(-1) != 5
                            || usage.totalTokenCount().orElse(-1) != 15) throw unavailable();
                    return new AgentBudgetSettlement(10L, 5L, new BigDecimal("0.05"));
                }
            };
        }

        @Bean("expenseFixtureDefinitionBootstrap")
        SmartInitializingSingleton expenseFixtureDefinitionBootstrap(DataSource dataSource, AgentLedgerService ledger,
                                                                    TrustedIssuerRegistry trust, Environment environment) {
            return () -> {
                if (!"true".equals(environment.getProperty(FIXTURE + "run-authorized"))
                        || !"AGENT".equals(environment.getProperty(FIXTURE + "role"))
                        || !ledger.usesDataSource(dataSource)) throw unavailable();
                var first = publisher(trust, environment, "tenant-1.");
                var second = publisher(trust, environment, "tenant-2.");
                if (first.tenantId() == second.tenantId() || first.issuerFingerprint().equals(second.issuerFingerprint())) throw unavailable();
                new AgentStorageVerifier(dataSource).verify();
                var definition = fixtureDefinition();
                for (var actor : List.of(first, second)) {
                    var stored = ledger.publish(actor, definition);
                    if (!DEFINITION.equals(stored.definitionKey()) || stored.version() != 1
                            || !definition.dependencies().equals(stored.dependencies())
                            || !stored.contentSha256().matches("[0-9a-f]{64}")) throw unavailable();
                }
            };
        }
    }

    static AgentDefinitionSpecDTO fixtureDefinition() {
        return new AgentDefinitionSpecDTO(DEFINITION, 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), ROUTE, POLICY),
                new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                        1, 0, 16_384, 128, new BigDecimal("0.20"), "USD", 1, Set.of(), null, null),
                Map.of("chatModelBeanName", MODEL_BEAN, "modelName", MODEL, "rootAgentName", "expenseAnalyzer",
                        "agents", List.of(Map.of("name", "expenseAnalyzer", "instruction", INSTRUCTION))));
    }

    private static TrustedAgentActor publisher(TrustedIssuerRegistry registry, Environment environment, String tenantKey) {
        String prefix = FIXTURE + tenantKey;
        String tenant = environment.getRequiredProperty(prefix + "id");
        String issuer = environment.getRequiredProperty(prefix + "issuer");
        var approved = registry.findByIssuer(issuer).orElseThrow(ExpenseAgentAcceptanceMain::unavailable);
        if (!tenant.matches("[1-9][0-9]{0,18}") || !tenant.equals(approved.tenantId())
                || !issuer.equals(approved.issuer().toString())
                || approved.status() != TrustedIssuerRegistry.Status.ACTIVE
                || approved.realmType() != TrustedIssuerRegistry.RealmType.TENANT) throw unavailable();
        return TrustedAgentActor.verified(Long.parseLong(tenant), TrustedAgentActor.SubjectType.USER,
                environment.getRequiredProperty(prefix + "subject"), issuer);
    }

    private static void requireModel(AgentExecutionSnapshot snapshot, LlmRequest request) {
        requireDefinition(snapshot);
        if (!MODEL.equals(request.model().orElse(null))) throw unavailable();
    }

    private static void requireDefinition(AgentExecutionSnapshot snapshot) {
        if (snapshot == null || !fixtureDefinition().equals(snapshot.definition())) throw unavailable();
    }

    private static long textBytes(com.google.genai.types.Content content) {
        long total = 0;
        var parts = content.parts().orElse(List.of());
        if (parts.size() > 16) throw unavailable();
        for (var part : parts) {
            String text = part.text().orElseThrow(ExpenseAgentAcceptanceMain::unavailable);
            if (text.length() > 8192 || !StandardCharsets.UTF_8.newEncoder().canEncode(text)) throw unavailable();
            total += text.getBytes(StandardCharsets.UTF_8).length;
        }
        return total;
    }

    private static int requestedAmount(Prompt prompt) {
        try {
            var messages = prompt.getInstructions().stream().filter(UserMessage.class::isInstance).toList();
            if (messages.size() != 1) throw invalidInput();
            String text = messages.getFirst().getText();
            if (text == null || text.length() > MAX_MODEL_INPUT_BYTES || !text.startsWith(MODEL_INPUT)
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(text)
                    || text.getBytes(StandardCharsets.UTF_8).length > MAX_MODEL_INPUT_BYTES) throw invalidInput();
            var root = JSON.readTree(text.substring(MODEL_INPUT.length()));
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("schemaVersion", "taskData"))
                    || !root.path("schemaVersion").isIntegralNumber() || !root.path("schemaVersion").canConvertToInt()
                    || root.path("schemaVersion").intValue() != 1) throw invalidInput();
            var task = root.path("taskData");
            if (!task.isObject() || !Set.copyOf(task.propertyNames()).equals(Set.of("requestedAmount"))) throw invalidInput();
            var amount = task.path("requestedAmount");
            if (!amount.isIntegralNumber() || !amount.canConvertToInt()
                    || !Set.of(100, 700, 1500, 200).contains(amount.intValue())) throw invalidInput();
            return amount.intValue();
        } catch (RuntimeException failure) {
            throw invalidInput();
        }
    }

    private static IllegalArgumentException invalidInput() {
        return new IllegalArgumentException(INVALID_INPUT);
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Expense Agent acceptance startup or fixture configuration failed");
    }

    private static Properties privateChildSettings(String[] args, String expectedRole) throws IOException {
        String location = "--spring.config.additional-location=";
        if (args == null || args.length != 2 || !"--spring.main.web-application-type=servlet".equals(args[0])
                || args[1] == null || !args[1].startsWith(location)
                || !"false".equals(System.getenv("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS"))) throw unavailable();
        Path root = Path.of("").toRealPath();
        Path config = privateFile(root, Path.of(URI.create(args[1].substring(location.length()))), 64 * 1024);
        String role = expectedRole.toLowerCase(java.util.Locale.ROOT);
        if (!config.getParent().equals(root)
                || !config.getFileName().toString().matches("expense-[A-Za-z0-9_.-]+-" + role + "-[1-9][0-9]*\\.properties")) {
            throw unavailable();
        }
        var settings = new Properties();
        try (var input = Files.newInputStream(config, LinkOption.NOFOLLOW_LINKS)) {
            byte[] contents = input.readNBytes(64 * 1024 + 1);
            try {
                if (contents.length == 0 || contents.length > 64 * 1024) throw unavailable();
                settings.load(new java.io.ByteArrayInputStream(contents));
            } finally {
                Arrays.fill(contents, (byte) 0);
            }
        }
        String address = settings.getProperty("server.address");
        String port = settings.getProperty("server.port");
        String jdbc = settings.getProperty("spring.datasource.url");
        if (!"true".equals(settings.getProperty(FIXTURE + "run-authorized"))
                || !expectedRole.equals(settings.getProperty(FIXTURE + "role"))
                || !("127.0.0.1".equals(address) || "::1".equals(address))
                || port == null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port) > 65_535
                || jdbc == null || !jdbc.startsWith("jdbc:postgresql://")
                || !"true".equals(settings.getProperty("server.ssl.enabled"))
                || !"false".equals(settings.getProperty("spring.flyway.enabled"))
                || !"never".equals(settings.getProperty("spring.sql.init.mode"))) throw unavailable();
        return settings;
    }

    private static Path privateFile(Path root, Path requested, long limit) throws IOException {
        if (!requested.isAbsolute() || !requested.equals(requested.normalize()) || !requested.startsWith(root)
                || !requested.equals(requested.toRealPath()) || !Files.isRegularFile(requested, LinkOption.NOFOLLOW_LINKS)
                || !Files.getOwner(root).equals(Files.getOwner(requested, LinkOption.NOFOLLOW_LINKS))
                || Files.size(requested) < 1 || Files.size(requested) > limit
                || !Files.getPosixFilePermissions(requested, LinkOption.NOFOLLOW_LINKS).stream()
                .allMatch(permission -> permission == PosixFilePermission.OWNER_READ || permission == PosixFilePermission.OWNER_WRITE)) {
            throw unavailable();
        }
        return requested;
    }

    private static void installTrust(Properties settings) throws Exception {
        Path root = Path.of("").toRealPath();
        Path trust = privateFile(root, Path.of(settings.getProperty(FIXTURE + "trust-store")), 1024 * 1024);
        Path passwordFile = privateFile(root, Path.of(settings.getProperty(FIXTURE + "trust-password-file")), 1024);
        byte[] bytes;
        try (var input = Files.newInputStream(passwordFile, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(1025);
        }
        char[] password = null;
        try {
            if (bytes.length > 1024) throw unavailable();
            var decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            int length = decoded.remaining();
            if (length > 0 && decoded.get(length - 1) == '\n') length--;
            if (length == 0) throw unavailable();
            password = new char[length];
            for (int i = 0; i < length; i++) {
                char character = decoded.get(i);
                if (Character.isISOControl(character)) throw unavailable();
                password[i] = character;
            }
            KeyStore trustStore = KeyStore.getInstance(trust.toFile(), password);
            var names = java.util.Collections.list(trustStore.aliases());
            if (names.isEmpty()) throw unavailable();
            for (String name : names) if (!trustStore.isCertificateEntry(name)) throw unavailable();
            var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            SSLContext.setDefault(context);
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (password != null) Arrays.fill(password, '\0');
        }
    }
}
