package top.egon.cola.platform.tianji.agent.live;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import top.egon.cola.platform.tianji.agent.si.TianshuTemplateResolver;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end live model test for gpt-6-luna using OpenAI platform API key from .env.
 */
class Gpt6LunaLiveIntegrationTest {

    private static String apiKey;

    record OrderQuery(String orderId) {}
    record OrderResult(String orderId, String status, double amount) {}

    @BeforeAll
    static void initKey() throws Exception {
        File env = new File("/Users/mario/SelfProject/Stellar-Enclosure/.env");
        if (env.exists()) {
            for (String line : Files.readAllLines(env.toPath())) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.contains(":") ? line.split(":", 2) : (line.contains("=") ? line.split("=", 2) : null);
                if (parts != null && parts[0].trim().toUpperCase().contains("OPENAI")) {
                    apiKey = parts[1].trim();
                    break;
                }
            }
        }
        if (apiKey == null) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "Skipping live test: No OpenAI API key found");
    }

    @Test
    void testSpringAiChatModelDirectCallWithGpt6Luna() {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(apiKey)
                .model("gpt-6-luna")
                .reasoningEffort("none")
                .maxCompletionTokens(150)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(options)
                .build();

        ChatResponse response = chatModel.call(new Prompt(new UserMessage("State the primary goal of Stellar Enclosure SI in 15 words.")));
        assertThat(response).isNotNull();
        assertThat(response.getResult()).isNotNull();
        String output = response.getResult().getOutput().getText();
        System.out.println("Spring AI gpt-6-luna direct output: " + output);
        assertThat(output).isNotBlank();
    }

    @Test
    void testSpringAiChatModelFunctionCallingWithGpt6Luna() {
        FunctionToolCallback<OrderQuery, OrderResult> tool = FunctionToolCallback
                .builder("lookup_order", (Function<OrderQuery, OrderResult>) q -> new OrderResult(q.orderId(), "CONFIRMED", 199.99))
                .description("Look up order status and amount by orderId")
                .inputType(OrderQuery.class)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(apiKey)
                .model("gpt-6-luna")
                .reasoningEffort("none")
                .maxCompletionTokens(250)
                .toolCallbacks(List.of(tool))
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(options)
                .build();

        // 1. Initial Prompt with Tool Definition
        Prompt prompt1 = new Prompt(new UserMessage("Check order ORD-7788 and report status and amount."));
        ChatResponse response1 = chatModel.call(prompt1);
        assertThat(response1).isNotNull();
        AssistantMessage assistantOutput = response1.getResult().getOutput();
        assertThat(assistantOutput.hasToolCalls()).isTrue();
        
        AssistantMessage.ToolCall toolCall = assistantOutput.getToolCalls().getFirst();
        System.out.println("Model generated ToolCall: " + toolCall.name() + " with args: " + toolCall.arguments());
        assertThat(toolCall.name()).isEqualTo("lookup_order");
        assertThat(toolCall.arguments()).contains("ORD-7788");

        // 2. Follow-up with simulated Tool Response Message
        ToolResponseMessage.ToolResponse toolResponse = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{\"orderId\":\"ORD-7788\",\"status\":\"CONFIRMED\",\"amount\":199.99}"
        );
        ToolResponseMessage toolMsg = ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build();
        Prompt prompt2 = new Prompt(List.of(new UserMessage("Check order ORD-7788 and report status and amount."), assistantOutput, toolMsg), options);
        ChatResponse response2 = chatModel.call(prompt2);
        assertThat(response2).isNotNull();
        String finalOutput = response2.getResult().getOutput().getText();
        System.out.println("Model final answer after tool response: " + finalOutput);
        assertThat(finalOutput).containsIgnoringCase("CONFIRMED");
        assertThat(finalOutput).contains("199.99");
    }

    @Test
    void testTianshuTemplateResolverWithGpt6LunaAndExecution() {
        DdcSiCatalogClient client = mock(DdcSiCatalogClient.class);
        TianshuTemplateResolver resolver = new TianshuTemplateResolver(client);

        String agentSpec = """
                {
                  "modelKey": "gpt-6-luna",
                  "reasoningEffort": "none",
                  "runtime": {"maxSteps": 4, "timeoutSeconds": 30},
                  "systemInstruction": "You are a specialized order management assistant."
                }
                """;

        DdcSiResourceDescriptor agent = DdcSiResourceDescriptor.builder()
                .tenantId(1001L)
                .bizCode("ai")
                .namespaceCode("core")
                .envCode("prod")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("order-support-agent")
                .displayName("Order Support Agent")
                .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                .activeVersionNo(1)
                .digestSha256("d".repeat(64))
                .specJson(agentSpec)
                .build();

        when(client.resolveResource(1001L, "ai", "core", "prod", "support-svc", DdcSiKindEnum.AGENT, "order-support-agent", null))
                .thenReturn(Optional.of(agent));

        TianshuTemplateResolver.ResolvedAgentExecutionBundle bundle = resolver.resolveBundle(
                1001L, "ai", "core", "prod", "support-svc", "order-support-agent", null);

        assertThat(bundle.modelKey()).isEqualTo("gpt-6-luna");
        assertThat(bundle.maxSteps()).isEqualTo(4);

        // Execute live prompt with this resolved configuration
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(apiKey)
                .model(bundle.modelKey())
                .reasoningEffort("none")
                .maxCompletionTokens(150)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(options)
                .build();

        ChatResponse response = chatModel.call(new Prompt(new UserMessage("State your role in one sentence.")));
        String answer = response.getResult().getOutput().getText();
        System.out.println("Resolved bundle live execution answer: " + answer);
        assertThat(answer).isNotBlank();
    }

    @Test
    void testSpringAiContextChatClientWithGpt6LunaAndThreeTierMemory() {
        // 1. Setup ChatModel with gpt-6-luna
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .apiKey(apiKey)
                .model("gpt-6-luna")
                .reasoningEffort("none")
                .maxCompletionTokens(200)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(options)
                .build();

        // 2. Setup Three-tier Memory fixtures & SpringAiContextChatClient
        var validationUtils = new top.egon.cola.component.common.core.validation.ValidationUtils(
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
        var clock = java.time.Clock.systemUTC();
        var repo = new top.egon.cola.component.agentflow.memory.InMemorySessionChatMemoryRepository(clock);
        var chatMemory = new top.egon.cola.component.agentflow.memory.TurnAlignedChatMemory(repo, 20, clock);
        var textService = new top.egon.cola.component.agentflow.memory.InMemoryTextMemoryService(clock, validationUtils, 10_000);
        var docReader = new top.egon.cola.component.agentflow.memory.InMemoryDocMemoryReader(validationUtils, 32);
        var engine = new top.egon.cola.component.agentflow.context.ContextAssemblyEngine(
                new org.springframework.ai.tokenizer.JTokkitTokenCountEstimator());
        var properties = new top.egon.cola.component.agentflow.autoconfigure.AgentFlowProperties(true, null, null, java.util.Map.of());

        // Level 1: History turn
        chatMemory.appendTurn("tenant-luna", "sess-luna-1", "turn-1", List.of(
                new UserMessage("Hello, my name is Alice."),
                new AssistantMessage("Hello Alice! How can I help you today?")
        ));

        // Level 2: Mid-term Text Memory
        var bound = textService.bindOrCreate(top.egon.cola.component.agentflow.api.memory.TextMemoryBindCommand.builder()
                .tenantKey("tenant-luna").namespace("crm")
                .ownerType(top.egon.cola.component.agentflow.api.memory.MemoryOwnerTypeEnum.AGENT).ownerKey("agent-luna").build());
        textService.save(top.egon.cola.component.agentflow.api.memory.TextMemorySaveCommand.builder()
                .tenantKey("tenant-luna").id(bound.id())
                .contentText("Alice loves green tea and works as a Senior Software Architect.")
                .expectedVersion(1L).build());

        // Level 3: Long-term Doc Memory
        docReader.put("tenant-luna", new top.egon.cola.component.agentflow.api.memory.DocMemoryVO(
                "doc-greeting-policy",
                "VIP Greeting Policy",
                "# VIP Policy\nAlways salute VIP guests with high respect and address them by their profession if known.",
                null,
                clock.instant()
        ), false);

        var client = new top.egon.cola.component.agentflow.adapter.springai.SpringAiContextChatClient(
                new top.egon.cola.component.agentflow.adapter.springai.ContextInitAdvisor(properties),
                new top.egon.cola.component.agentflow.adapter.springai.SessionHistoryAdvisor(chatMemory),
                new top.egon.cola.component.agentflow.adapter.springai.TextMemoryAdvisor(textService),
                new top.egon.cola.component.agentflow.adapter.springai.DocMemoryAdvisor(docReader),
                new top.egon.cola.component.agentflow.adapter.springai.OthersContextAdvisor(),
                new top.egon.cola.component.agentflow.adapter.springai.ContextAssemblyAdvisor(engine),
                validationUtils,
                properties
        );

        var scope = new top.egon.cola.component.agentflow.api.context.ContextScopeVO(
                "crm", "tenant-luna", "user-alice", "agent-luna", "sess-luna-1", "chat");

        var callBO = top.egon.cola.component.agentflow.adapter.springai.SpringAiContextChatClient.AgentContextCallBO.builder()
                .chatModel(chatModel)
                .scope(scope)
                .systemPrompt("You are an intelligent AI concierge.")
                .userPrompt("What is my name and what beverage do I like?")
                .textMemoryId(bound.id())
                .docMemoryIds(List.of("doc-greeting-policy"))
                .build();

        var result = client.call(callBO);
        assertThat(result).isNotNull();
        String answer = result.content();
        System.out.println("SpringAiContextChatClient with gpt-6-luna and 3-tier memory answer: " + answer);
        assertThat(answer).containsIgnoringCase("Alice");
        assertThat(answer).containsIgnoringCase("tea");

        // Verify Level 1 session memory was automatically appended with the new turn
        var historyTurns = chatMemory.getTurns("tenant-luna", "sess-luna-1", 10);
        assertThat(historyTurns).hasSize(2);
        System.out.println("Level 1 turn count after call: " + historyTurns.size());
    }
}
