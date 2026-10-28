package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentClient;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientDependencyBoundaryTest {

    @Test
    void client_and_contract_classpath_excludes_engines_and_ai_frameworks() {
        for (String type : new String[]{"com.google.adk.agents.BaseAgent", "org.flowable.engine.ProcessEngine",
                "org.springframework.ai.chat.model.ChatModel", "reactor.core.publisher.Flux",
                "io.reactivex.rxjava3.core.Flowable", "com.fasterxml.jackson.databind.JsonNode"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
        }
    }

    @Test
    void agent_client_signatures_do_not_expose_engine_types() {
        for (Method method : AgentClient.class.getMethods()) {
            assertFalse(method.toGenericString().contains("com.google.adk"));
            assertFalse(method.toGenericString().contains("org.flowable"));
            assertFalse(method.toGenericString().contains("org.springframework.ai"));
        }
    }
}
