package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessDependencyBoundaryTest {
    @Test
    void process_host_contains_its_engines_but_not_the_agent_or_sharding_runtime() {
        assertDoesNotThrow(() -> Class.forName("org.flowable.engine.ProcessEngine"));
        assertDoesNotThrow(() -> Class.forName("org.flowable.cmmn.engine.CmmnEngine"));
        assertDoesNotThrow(() -> Class.forName("org.flowable.dmn.engine.DmnEngine"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.google.adk.agents.BaseAgent"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.springframework.ai.chat.model.ChatModel"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.apache.shardingsphere.driver.ShardingSphereDriver"));
    }
}
