package top.egon.cola.platform.tianji.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerDependencyBoundaryTest {
    @Test
    void business_worker_classpath_does_not_include_execution_engines_or_model_frameworks() {
        for (String type : new String[] {"org.flowable.engine.ProcessEngine", "com.google.adk.agents.BaseAgent",
                "org.springframework.ai.chat.model.ChatModel"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
        }
    }
}
