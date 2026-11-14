package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultClient;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentCloseResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartResult;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseClient;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerClient;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientDependencyBoundaryTest {

    @Test
    void client_and_contract_classpath_excludes_engines_and_ai_frameworks() {
        for (String type : new String[]{"com.google.adk.agents.BaseAgent", "org.flowable.engine.ProcessEngine",
                "org.springframework.ai.chat.model.ChatModel", "reactor.core.publisher.Flux",
                "io.reactivex.rxjava3.core.Flowable", "com.fasterxml.jackson.databind.JsonNode",
                "org.springframework.context.ApplicationContext", "org.springframework.jdbc.core.JdbcTemplate",
                "jakarta.persistence.EntityManager"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
        }
    }

    @Test
    void agent_client_signatures_do_not_expose_engine_types() {
        for (Class<?> contract : new Class<?>[] {AgentClient.class, ProcessClient.class, ProcessReleaseClient.class,
                DecisionClient.class, WorkerClient.class, AutomationWorkerClient.class, LinkedAgentClient.class, AgentResultClient.class,
                HttpAutomationWorkerClient.class, HttpLinkedAgentClient.class, HttpAgentResultClient.class}) {
            for (Method method : contract.getMethods()) {
                for (String forbidden : new String[]{"com.google.adk", "org.flowable", "org.springframework", "jakarta.persistence",
                        "tools.jackson", "java.sql"}) {
                    assertFalse(method.toGenericString().contains(forbidden), method::toGenericString);
                }
            }
        }
    }

    @Test
    void legacy_linked_agent_and_inbox_method_descriptors_remain_available() throws NoSuchMethodException {
        assertEquals(LinkedAgentStartResult.class, LinkedAgentClient.class.getMethod("submit", LinkedAgentStartCommand.class).getReturnType());
        assertEquals(LinkedAgentLookupResult.class, LinkedAgentClient.class.getMethod("resolve", LinkedAgentLookupQuery.class).getReturnType());
        assertEquals(LinkedAgentCloseResult.class, LinkedAgentClient.class.getMethod("close", LinkedAgentLookupQuery.class).getReturnType());
        assertEquals(AgentResultReceipt.class, AgentResultClient.class.getMethod("receive", AgentTerminalEvent.class).getReturnType());
    }
}
