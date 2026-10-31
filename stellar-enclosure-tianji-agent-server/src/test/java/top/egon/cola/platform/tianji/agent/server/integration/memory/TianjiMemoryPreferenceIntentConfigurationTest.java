package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceWriteAuthorization;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiMemoryPreferenceIntentConfigurationTest {
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private static final MemoryRetentionPolicy RETENTION = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100, Set.of(), Set.of(), false);
    private final DataSource source = mock(DataSource.class);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryPreferenceIntentStore store = mock(MemoryPreferenceIntentStore.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);

    @BeforeEach void setup() {
        when(ledger.usesDataSource(source)).thenReturn(true); when(store.usesDataSource(source)).thenReturn(true); when(store.usesLedger(ledger)).thenReturn(true);
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
    }

    @Test void management_is_default_off_and_can_verify_old_intents_without_background_runtime_or_source_configuration() {
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryPreferenceIntentConfiguration.class).run(context -> assertTrue(context.getBeansOfType(MemoryPreferenceIntentAccess.class).isEmpty()));
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryPreferenceIntentConfiguration.class).withPropertyValues("stellar.tianji.agent.memory.extraction.intents.http.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
        runner().run(context -> {
            assertNull(context.getStartupFailure()); assertEquals(1, context.getBeansOfType(MemoryPreferenceIntentStore.class).size()); assertNotNull(context.getBean(MemoryPreferenceIntentAccess.class));
            assertTrue(context.getBeansOfType(MemoryPreferenceJob.class).isEmpty()); assertTrue(context.getBeansOfType(TianjiMemoryPreferenceGrantIssuer.class).isEmpty()); verify(store).verifyStorage();
        });
        doReturn(false).when(store).usesDataSource(source); runner().run(context -> assertNotNull(context.getStartupFailure()));
        verify(ledger, never()).executionMaterial(any(), any()); verifyNoInteractions(source);
    }

    @Test void management_and_background_share_one_store_and_finish_schema_readiness_before_manual_scheduling() {
        var memory = mock(PostgresMemoryService.class); when(memory.usesDataSource(source)).thenReturn(true);
        var writes = mock(TianjiMemoryPreferenceWriteAuthorization.class); when(writes.usesLedger(ledger)).thenReturn(true); when(writes.usesBindings(anyList(), anyList())).thenReturn(true);
        var issuer = mock(TianjiMemoryPreferenceGrantIssuer.class); when(issuer.usesLedger(ledger)).thenReturn(true); when(issuer.usesBindings(anyList())).thenReturn(true);
        var submissions = mock(TianjiExecutionGrantIssuer.class); when(submissions.usesPreferenceIssuer(issuer)).thenReturn(true);
        var constructed = new java.util.concurrent.atomic.AtomicReference<MemoryPreferenceIntentStore>();
        var scheduler = mock(TaskScheduler.class); when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(call -> { verify(constructed.get(), times(2)).verifyStorage(); return mock(ScheduledFuture.class); });
        var policy = new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION.reference(), 1);
        String prefix = "stellar.tianji.agent.memory.extraction.";
        try (var stores = mockConstruction(MemoryPreferenceIntentStore.class, (value, ignored) -> {
            constructed.set(value); when(value.usesDataSource(source)).thenReturn(true); when(value.usesLedger(ledger)).thenReturn(true);
        })) {
            runner(false).withUserConfiguration(TianjiMemoryPreferenceBackgroundConfiguration.class)
                    .withBean(PostgresMemoryService.class, () -> memory).withBean(TianjiMemoryPreferenceWriteAuthorization.class, () -> writes).withBean(TianjiMemoryPreferenceGrantIssuer.class, () -> issuer).withBean(TianjiExecutionGrantIssuer.class, () -> submissions)
                    .withBean("tianjiAgentReconciliationScheduler", TaskScheduler.class, () -> scheduler)
                    .withPropertyValues(prefix + "background.enabled=true", "stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.execution-grants.enabled=true", "stellar.tianji.agent.execution-authorization.enabled=true", "stellar.tianji.agent.reconciliation.enabled=true",
                            prefix + "policies[0].key=preferences", prefix + "policies[0].version=1", prefix + "policies[0].preference-values.language[0]=zh-CN", prefix + "policies[0].candidate-confidence=0.5", prefix + "policies[0].sensitivity=CONFIDENTIAL", prefix + "policies[0].retention-policy-ref=" + RETENTION.reference(), prefix + "policies[0].maximum-candidates=1",
                            prefix + "bindings[0].id=source", prefix + "bindings[0].tenant-id=20", prefix + "bindings[0].issuer-fingerprint=" + ACTOR.issuerFingerprint(), prefix + "bindings[0].definition-key=assistant", prefix + "bindings[0].definition-version=1", prefix + "bindings[0].definition-sha256=" + "a".repeat(64), prefix + "bindings[0].scope-id=research", prefix + "bindings[0].policy-ref=" + policy.reference())
                    .run(context -> { assertNull(context.getStartupFailure()); assertEquals(1, context.getBeansOfType(MemoryPreferenceIntentStore.class).size()); assertEquals(1, stores.constructed().size()); assertTrue(context.getBean(MemoryPreferenceJob.class).isRunning()); verify(constructed.get(), never()).candidates(anyLong(), anyString(), anyInt()); });
        }
    }

    private ApplicationContextRunner runner() { return runner(true); }
    private ApplicationContextRunner runner(boolean suppliedStore) {
        var storage = new TianjiMemoryProperties(2, "0.8.0", null, 1, new VersionedMemoryEmbeddingModel.Profile("memory", 1, "embedding", "text-embedding-3-small", 2, 8191, 64, 16), List.of(RETENTION), List.of(new TianjiMemoryProperties.Origin(20, ISSUER)));
        var runner = new ApplicationContextRunner().withUserConfiguration(TianjiMemoryPreferenceIntentConfiguration.class).withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger)
                .withBean(TianjiAgentAccess.class, () -> mock(TianjiAgentAccess.class)).withBean(TianjiMemoryProperties.class, () -> storage).withBean(TrustedIssuerRegistry.class, () -> issuers)
                .withPropertyValues("stellar.tianji.agent.memory.extraction.intents.http.enabled=true", "stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.storage.enabled=true", "stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.memory.http.enabled=true");
        return suppliedStore ? runner.withBean(MemoryPreferenceIntentStore.class, () -> store) : runner;
    }
}
