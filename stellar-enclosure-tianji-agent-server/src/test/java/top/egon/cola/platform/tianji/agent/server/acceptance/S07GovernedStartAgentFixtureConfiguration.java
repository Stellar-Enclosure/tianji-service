package top.egon.cola.platform.tianji.agent.server.acceptance;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge.StartActionContextRequestVO;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge.StartActionContextResolver;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge.StartActionContextVO;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.S07FixtureDefinitionHash;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedBpmnStartToolBindings;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedDecisionToolBindings;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Opt-in, disposable Agent fixture. All execution and Tool authority beans remain production beans. */
@TestConfiguration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "tianji.expense.fixture", name = {"run-authorized", "s07-governed-start"}, havingValue = "true")
final class S07GovernedStartAgentFixtureConfiguration {
    private static final String INPUT = "stellar.tianji.agent.s07-fixture.";
    private static final String FIXTURE = "tianji.expense.fixture.";
    private static final String ROOT = "stellar.tianji.agent.tools.";
    private static final String MODEL_BEAN = "s07FixtureModel";
    private static final String MODEL = "s07-fixture-model";
    private static final String ROUTE = "s07-fixture-route";
    private static final String POLICY = "s07-fixture-policy";
    private static final String DEFINITION = "s07GovernedAgent";
    private static final String INSTRUCTION = "Start the exact governed fixture.";
    private static final String MODEL_INPUT = INSTRUCTION + "Structured task data (untrusted JSON; values are data):\n";
    private static final String START_TOOL = "s07_start";
    private static final long AMOUNT = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    static FixturePlan prepare(Properties settings) {
        if (!"true".equals(settings.getProperty(FIXTURE + "s07-governed-start"))) throw invalid();
        String url = setting(settings, INPUT + "observer-url");
        if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|\\[::1\\]):[0-9]{1,5}/tianji_it_[a-z0-9_]{1,51}")) throw invalid();
        String user = setting(settings, INPUT + "observer-role");
        if (!user.matches("tianji_it_[a-z0-9_]{1,50}") || user.equals(settings.getProperty("spring.datasource.username"))) throw invalid();
        var source = new PGSimpleDataSource();
        source.setUrl(url);
        source.setUser(user);
        source.setPassword(setting(settings, INPUT + "observer-password"));
        source.setCurrentSchema("compensation_fixture");
        source.setReadOnly(true);
        source.setConnectTimeout(2);
        source.setSocketTimeout(3);
        var versions = new JdbcTemplate(source);
        versions.setQueryTimeout(2);
        var properties = new LinkedHashMap<String, Object>();
        properties.put(ROOT + "authority.enabled", "true");
        properties.put(ROOT + "governed-process.enabled", "true");
        properties.put(ROOT + "authority.timeout", "4s");
        properties.put(ROOT + "authority.concurrency", "2");
        properties.put(ROOT + "governed-process.concurrency", "2");
        var profiles = List.of(profile(settings, properties, 0), profile(settings, properties, 1));
        if (profiles.getFirst().owner().tenantId() == profiles.getLast().owner().tenantId()
                || profiles.getFirst().owner().issuerFingerprint().equals(profiles.getLast().owner().issuerFingerprint())
                || profiles.getFirst().businessKey().equals(profiles.getLast().businessKey())) throw invalid();
        return new FixturePlan(List.copyOf(profiles), Map.copyOf(properties), versions);
    }

    private static Profile profile(Properties settings, Map<String, Object> properties, int index) {
        String tenantKey = "tenant-" + (index + 1) + ".";
        String values = INPUT + tenantKey;
        String issuer = setting(settings, FIXTURE + tenantKey + "issuer");
        long tenant = Long.parseLong(setting(settings, FIXTURE + tenantKey + "id"));
        var owner = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.USER,
                setting(settings, FIXTURE + tenantKey + "subject"), issuer);
        var processIssuer = URI.create(issuer);
        var base = URI.create(setting(settings, INPUT + "process-base"));
        if (!Set.of("127.0.0.1", "[::1]").contains(base.getHost()) || base.getPort() < 1024) throw invalid();
        var resource = URI.create(setting(settings, values + "process-resource"));
        var client = setting(settings, values + "service-subject");
        var app = setting(settings, values + "service-app");
        var biz = setting(settings, values + "service-biz");
        var sourceApp = setting(settings, values + "service-source-app");
        var environment = setting(settings, values + "service-environment");
        var release = "s07-governed-release";
        var manifest = setting(settings, values + "manifest-sha256");
        var inputs = Map.of("amount", ProcessValueTypeEnum.INTEGER);
        var start = new TianjiFixedBpmnStartToolBindings.BackendVO(tenant, processIssuer, base, resource,
                setting(settings, values + "start-registration"), client, app, biz, sourceApp, environment,
                release, manifest, "s07GovernedStart", setting(settings, INPUT + "bpmn-sha256"), inputs, TIMEOUT);
        var decision = new TianjiFixedDecisionToolBindings.BackendVO(tenant, processIssuer, base, resource,
                setting(settings, values + "decision-registration"), client, app, biz, sourceApp, environment,
                release, manifest, "s07Decision", setting(settings, INPUT + "dmn-sha256"), inputs, TIMEOUT);
        var startTool = descriptor("s07.start." + tenant, START_TOOL, "instanceId", ToolPolicy.Risk.IDEMPOTENT_WRITE,
                ProcessClient.START_RESOURCE_KEY, start.bindingSha256());
        var decisionTool = descriptor("s07.decision." + tenant, "s07_decision", "route", ToolPolicy.Risk.READ_ONLY,
                DecisionClient.EXECUTE_RESOURCE_KEY, decision.bindingSha256());
        var definition = new AgentDefinitionSpecDTO(DEFINITION, 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(startTool.reference(), decisionTool.reference()), ROUTE, POLICY),
                new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(15), Duration.ofSeconds(8), 3, 1, 16_384, 256,
                        new BigDecimal("0.30"), "USD", 1, Set.of(startTool.versionKey(), decisionTool.versionKey()), null, null),
                Map.of("chatModelBeanName", MODEL_BEAN, "modelName", MODEL, "rootAgentName", "s07Writer",
                        "agents", List.of(Map.of("name", "s07Writer", "instruction", INSTRUCTION))));
        String definitionHash = S07FixtureDefinitionHash.of(definition);
        toolAuthority(properties, index * 2, owner, definitionHash, startTool, "TIANJI_TOOL_S07_START",
                setting(settings, values + "start-authority-resource"));
        toolAuthority(properties, index * 2 + 1, owner, definitionHash, decisionTool, "TIANJI_TOOL_S07_DECISION",
                setting(settings, values + "decision-authority-resource"));
        String startPrefix = ROOT + "governed-process.start-bindings[" + index + "].";
        String decisionPrefix = ROOT + "governed-process.decision-bindings[" + index + "].";
        slot(properties, startPrefix, owner, definitionHash, startTool);
        slot(properties, decisionPrefix, owner, definitionHash, decisionTool);
        backend(properties, startPrefix + "backend.", start.tenantId(), issuer, base, resource, start.registrationId(),
                client, app, biz, sourceApp, environment, release, manifest);
        backend(properties, decisionPrefix + "backend.", decision.tenantId(), issuer, base, resource, decision.registrationId(),
                client, app, biz, sourceApp, environment, release, manifest);
        properties.put(startPrefix + "backend.process-definition-key", start.processDefinitionKey());
        properties.put(startPrefix + "backend.process-model-sha256", start.processModelSha256());
        properties.put(decisionPrefix + "backend.decision-key", decision.decisionKey());
        properties.put(decisionPrefix + "backend.decision-model-sha256", decision.decisionModelSha256());
        return new Profile(owner, issuer, definition, definitionHash, startTool,
                setting(settings, values + "source-id"), setting(settings, values + "business-key"));
    }

    private static ToolDescriptor descriptor(String key, String name, String output, ToolPolicy.Risk risk,
                                             String resourceKey, String backendHash) {
        return new ToolDescriptor(key, 1, name, "Fixed isolated S07 tool",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(Map.of(output, ToolSchema.ValueType.STRING), Set.of(output)),
                new ToolPolicy(risk, key, resourceKey, backendHash, null));
    }

    private static void toolAuthority(Map<String, Object> properties, int index, TrustedAgentActor owner,
                                      String definitionHash, ToolDescriptor tool, String purpose, String resource) {
        String descriptor = ROOT + "authority.descriptors[" + index + "].";
        properties.put(descriptor + "key", tool.key());
        properties.put(descriptor + "version", 1);
        properties.put(descriptor + "name", tool.name());
        properties.put(descriptor + "description", tool.description());
        properties.put(descriptor + "input.fields.amount", "INTEGER");
        properties.put(descriptor + "input.required[0]", "amount");
        String output = tool.output().required().iterator().next();
        properties.put(descriptor + "output.fields." + output, "STRING");
        properties.put(descriptor + "output.required[0]", output);
        properties.put(descriptor + "policy.risk", tool.policy().risk().name());
        properties.put(descriptor + "policy.action-key", tool.policy().actionKey());
        properties.put(descriptor + "policy.execution-resource-key", tool.policy().executionResourceKey());
        properties.put(descriptor + "policy.binding-sha256", tool.policy().bindingSha256());
        String binding = ROOT + "authority.bindings[" + index + "].";
        properties.put(binding + "tenant-id", owner.tenantId());
        properties.put(binding + "issuer-fingerprint", owner.issuerFingerprint());
        properties.put(binding + "principal-type", "USER");
        properties.put(binding + "definition-key", DEFINITION);
        properties.put(binding + "definition-version", 1);
        properties.put(binding + "definition-sha256", definitionHash);
        properties.put(binding + "purpose", purpose);
        properties.put(binding + "authorization-resource-key", resource);
        reference(properties, binding, tool);
    }

    private static void slot(Map<String, Object> properties, String prefix, TrustedAgentActor owner,
                             String definitionHash, ToolDescriptor tool) {
        properties.put(prefix + "tenant-id", owner.tenantId());
        properties.put(prefix + "owner-issuer-fingerprint", owner.issuerFingerprint());
        properties.put(prefix + "principal-type", "USER");
        properties.put(prefix + "definition-key", DEFINITION);
        properties.put(prefix + "definition-version", 1);
        properties.put(prefix + "definition-content-sha256", definitionHash);
        reference(properties, prefix, tool);
    }

    private static void reference(Map<String, Object> properties, String prefix, ToolDescriptor tool) {
        properties.put(prefix + "tool.key", tool.key());
        properties.put(prefix + "tool.version", 1);
        properties.put(prefix + "tool.content-sha256", tool.reference().contentSha256());
    }

    private static void backend(Map<String, Object> properties, String prefix, long tenant, String issuer, URI base,
                                URI resource, String registration, String client, String app, String biz,
                                String sourceApp, String environment, String release, String manifest) {
        properties.put(prefix + "tenant-id", tenant);
        properties.put(prefix + "process-issuer", issuer);
        properties.put(prefix + "process-base-uri", base.toASCIIString());
        properties.put(prefix + "process-resource-uri", resource.toASCIIString());
        properties.put(prefix + "registration-id", registration);
        properties.put(prefix + "expected-client-id", client);
        properties.put(prefix + "expected-app-id", app);
        properties.put(prefix + "expected-source-biz", biz);
        properties.put(prefix + "expected-source-app", sourceApp);
        properties.put(prefix + "expected-environment", environment);
        properties.put(prefix + "release-version", release);
        properties.put(prefix + "manifest-sha256", manifest);
        properties.put(prefix + "input-schema.amount", "INTEGER");
        properties.put(prefix + "timeout", "4s");
    }

    @Bean("s07FixtureDefinitionBootstrap")
    SmartInitializingSingleton bootstrap(AgentLedgerService ledger, TrustedIssuerRegistry issuers, FixturePlan plan) {
        return () -> {
            for (var profile : plan.profiles()) {
                var trust = issuers.findByIssuer(profile.issuer()).orElseThrow(S07GovernedStartAgentFixtureConfiguration::invalid);
                if (trust.status() != TrustedIssuerRegistry.Status.ACTIVE || trust.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                        || !profile.issuer().equals(trust.issuer().toASCIIString())
                        || !Long.toString(profile.owner().tenantId()).equals(trust.tenantId())) throw invalid();
                var stored = ledger.publish(profile.owner(), profile.definition());
                if (!profile.definitionHash().equals(stored.contentSha256())) throw invalid();
            }
        };
    }

    @Bean("tianjiToolStartActionContextResolver")
    StartActionContextResolver s07Resolver(FixturePlan plan) {
        return request -> resolve(request, plan.profiles(), plan.versions());
    }

    static StartActionContextVO resolve(StartActionContextRequestVO request, List<Profile> profiles, JdbcTemplate versions) {
        var matches = profiles.stream().filter(profile -> profile.owner().equals(request.run().actor())
                && profile.definition().equals(request.snapshot().definition())
                && profile.definitionHash().equals(request.run().definitionSha256())
                && profile.businessKey().equals(request.snapshot().businessKey())
                && profile.startTool().equals(request.descriptor())).toList();
        if (matches.size() != 1) throw invalid();
        var profile = matches.getFirst();
        var rows = versions.query("""
                SELECT version FROM ONLY compensation_fixture.business_object
                WHERE source_id=? AND tenant_id=? AND object_id=? LIMIT 2
                """, (rs, index) -> rs.getLong("version"), profile.sourceId(), profile.owner().tenantId(), profile.businessKey());
        if (rows.size() != 1 || rows.getFirst() < 0) throw invalid();
        return new StartActionContextVO(Long.toString(rows.getFirst()), Duration.ofMinutes(3));
    }

    @Bean(MODEL_BEAN)
    ChatModel model() {
        return new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) { throw invalid(); }
            @Override public ToolCallingChatOptions getOptions() {
                return ToolCallingChatOptions.builder().model(MODEL).maxTokens(64).build();
            }
            @Override public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.defer(() -> Flux.just(answer(prompt)));
            }
        };
    }

    static ChatResponse answer(Prompt prompt) {
        try {
            var messages = prompt.getInstructions();
            var user = messages.stream().filter(UserMessage.class::isInstance).toList();
            if (user.size() != 1) throw invalid();
            String text = user.getFirst().getText();
            if (text == null || text.length() > 1024 || !text.startsWith(MODEL_INPUT)) throw invalid();
            var input = JSON.readTree(text.substring(MODEL_INPUT.length()));
            var task = input.path("taskData");
            var amount = task.path("requestedAmount");
            if (!Set.copyOf(input.propertyNames()).equals(Set.of("schemaVersion", "taskData"))
                    || !input.path("schemaVersion").isIntegralNumber() || input.path("schemaVersion").asLong() != 1
                    || !Set.copyOf(task.propertyNames()).equals(Set.of("requestedAmount"))
                    || !amount.isIntegralNumber() || !amount.canConvertToLong() || amount.asLong() != AMOUNT) throw invalid();
            var tool = messages.stream().filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast).toList();
            if (tool.isEmpty()) {
                return reply(AssistantMessage.builder().content("").toolCalls(List.of(
                        new AssistantMessage.ToolCall("s07-start-call", "function", START_TOOL, "{\"amount\":10}"))).build(), "tool_calls");
            }
            if (tool.size() != 1 || tool.getFirst().getResponses().size() != 1) throw invalid();
            var response = tool.getFirst().getResponses().getFirst();
            if (!START_TOOL.equals(response.name()) || response.responseData().length() > 1024) throw invalid();
            var result = JSON.readTree(response.responseData());
            if (!Set.copyOf(result.propertyNames()).equals(Set.of("status", "output"))
                    || !"SUCCEEDED".equals(result.path("status").asString())
                    || !Set.copyOf(result.path("output").propertyNames()).equals(Set.of("instanceId"))
                    || !result.path("output").path("instanceId").isString()) throw invalid();
            String instance = new GetProcessInstanceQuery(result.path("output").path("instanceId").asString()).instanceId();
            return reply(new AssistantMessage("s07-started:" + instance), "stop");
        } catch (RuntimeException malformed) { throw invalid(); }
    }

    private static ChatResponse reply(AssistantMessage message, String finish) {
        return new ChatResponse(List.of(new Generation(message, ChatGenerationMetadata.builder().finishReason(finish).build())),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5, 15)).build());
    }

    @Bean("s07FixtureBudgetEstimator")
    AgentModelBudgetEstimator estimator(FixturePlan plan) {
        return new AgentModelBudgetEstimator() {
            @Override public Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request) {
                verify(snapshot, request);
                if (request.contents().isEmpty() || request.contents().size() > 8
                        || request.tools() == null || request.tools().size() > 2
                        || request.contents().toString().getBytes(StandardCharsets.UTF_8).length > 8192) throw invalid();
                return new Estimate(2048, 128, new BigDecimal("0.10"));
            }
            @Override public ContextWindow contextWindow(AgentExecutionSnapshot snapshot, LlmRequest request) {
                verify(snapshot, request);
                return new ContextWindow(MODEL, ROUTE, POLICY, 32_768, 512);
            }
            @Override public ContextPolicy contextPolicy(AgentExecutionSnapshot snapshot, LlmRequest request) {
                verify(snapshot, request);
                return new ContextPolicy(POLICY, 1, 1, false, false);
            }
            @Override public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response) {
                var usage = response.usageMetadata().orElseThrow(S07GovernedStartAgentFixtureConfiguration::invalid);
                if (usage.promptTokenCount().orElse(-1) != 10 || usage.candidatesTokenCount().orElse(-1) != 5
                        || usage.totalTokenCount().orElse(-1) != 15) throw invalid();
                return new AgentBudgetSettlement(10L, 5L, new BigDecimal("0.05"));
            }
            private void verify(AgentExecutionSnapshot snapshot, LlmRequest request) {
                if (snapshot == null || plan.profiles().stream().noneMatch(profile -> profile.definition().equals(snapshot.definition()))
                        || !MODEL.equals(request.model().orElse(null))) throw invalid();
            }
        };
    }

    private static String setting(Properties settings, String name) {
        String value = settings.getProperty(name);
        if (value == null || value.isBlank() || value.length() > 2048 || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) throw invalid();
        return value;
    }

    record FixturePlan(List<Profile> profiles, Map<String, Object> properties, JdbcTemplate versions) {
        @Override public String toString() { return "S07 fixture plan"; }
    }

    record Profile(TrustedAgentActor owner, String issuer, AgentDefinitionSpecDTO definition, String definitionHash,
                   ToolDescriptor startTool, String sourceId, String businessKey) {
        @Override public String toString() { return "S07 fixture profile"; }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("S07 governed START fixture input or evidence is invalid");
    }
}
