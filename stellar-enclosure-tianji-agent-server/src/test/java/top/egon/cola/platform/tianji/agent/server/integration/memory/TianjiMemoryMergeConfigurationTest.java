package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.server.api.MemoryMergeController;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiMemoryMergeConfigurationTest {
    @Test void comparison_is_default_off_and_cannot_bypass_the_existing_memory_http_boundaries() {
        var memory = mock(PostgresMemoryService.class); var access = mock(TianjiAgentAccess.class);
        var runner = new ApplicationContextRunner().withUserConfiguration(TianjiMemoryMergeConfiguration.class, MemoryMergeController.class)
                .withBean(PostgresMemoryService.class, () -> memory).withBean(TianjiAgentAccess.class, () -> access);
        runner.run(context -> assertTrue(context.getBeansOfType(MemoryMergeController.class).isEmpty()));
        runner.withPropertyValues("stellar.tianji.agent.memory.merge-suggestions.http.enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("stellar.tianji.agent.memory.merge-suggestions.http.enabled=true", "stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.memory.http.enabled=true")
                .run(context -> { assertNull(context.getStartupFailure()); assertEquals(1, context.getBeansOfType(MemoryMergeController.class).size()); });
        verifyNoInteractions(memory, access);
    }
}
