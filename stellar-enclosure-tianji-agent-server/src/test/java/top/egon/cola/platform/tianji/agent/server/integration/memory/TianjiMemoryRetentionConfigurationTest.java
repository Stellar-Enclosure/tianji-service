package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiMemoryRetentionConfigurationTest {
    @Test void retention_is_default_off_and_uses_only_an_explicit_policy_on_the_existing_scheduler() {
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryRetentionConfiguration.class).run(context -> assertTrue(context.getBeansOfType(MemoryRetentionJob.class).isEmpty()));
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryRetentionConfiguration.class).withPropertyValues("stellar.tianji.agent.memory.retention.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
        var source = mock(DataSource.class); var memory = mock(PostgresMemoryService.class); var publication = mock(OutboxMemoryIndexPublication.class); var scheduler = mock(TaskScheduler.class); var scheduled = mock(ScheduledFuture.class);
        var policy = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100, Set.of(MemorySensitivityEnum.CONFIDENTIAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);
        var binding = new MemoryIndexPublication.Binding("embedding@1#" + "a".repeat(64), 2, "agent_memory_vector_v1");
        when(memory.usesDataSource(source)).thenReturn(true); when(memory.indexBinding()).thenReturn(binding); when(memory.policyReferences()).thenReturn(List.of(policy.reference()));
        when(publication.usesDataSource(source)).thenReturn(true); when(publication.binding()).thenReturn(binding);
        doReturn(scheduled).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        var storage = new TianjiMemoryProperties(2, "0.8.0", null, 1, new VersionedMemoryEmbeddingModel.Profile("embedding", 1, "model", "text-embedding-3-small", 2, 8191, 64, 16),
                List.of(policy), List.of(new TianjiMemoryProperties.Origin(20, URI.create("https://idp.example/20"))));
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryRetentionConfiguration.class).withBean(DataSource.class, () -> source).withBean(PostgresMemoryService.class, () -> memory)
                .withBean(OutboxMemoryIndexPublication.class, () -> publication).withBean(TianjiMemoryProperties.class, () -> storage).withBean("tianjiAgentReconciliationScheduler", TaskScheduler.class, () -> scheduler)
                .withPropertyValues("stellar.tianji.agent.memory.retention.enabled=true", "stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.reconciliation.enabled=true", "stellar.tianji.agent.memory.retention.policy-refs[0]=" + policy.reference())
                .run(context -> { assertNull(context.getStartupFailure()); assertTrue(context.getBean(MemoryRetentionJob.class).isRunning()); verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofMinutes(1))); });
        verify(scheduled).cancel(false); verifyNoInteractions(source); verify(publication, never()).publish(any());
        assertThrows(IllegalArgumentException.class, () -> new TianjiMemoryRetentionProperties(Set.of(), 1, null, null, null));
    }
}
