package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceAdmission;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TianjiMemoryPreferenceConfigurationTest {
    @Test void http_admission_is_default_off_and_requires_all_existing_memory_and_api_boundaries() {
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryPreferenceConfiguration.class).run(context -> assertTrue(context.getBeansOfType(MemoryPreferenceAdmission.class).isEmpty()));
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryPreferenceConfiguration.class).withPropertyValues("stellar.tianji.agent.memory.extraction.http.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test void actual_property_binding_requires_known_policies_same_storage_and_current_tenant_origin_without_io() {
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class); var memory = mock(PostgresMemoryService.class); var issuers = mock(TrustedIssuerRegistry.class);
        var issuer = URI.create("https://idp.example/20"); var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", issuer.toASCIIString());
        var retention = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100, Set.of(), Set.of(MemorySensitivityEnum.CONFIDENTIAL), false);
        var policy = new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, retention.reference(), 3);
        when(ledger.usesDataSource(source)).thenReturn(true); when(memory.usesDataSource(source)).thenReturn(true); when(memory.policyReferences()).thenReturn(List.of(retention.reference()));
        when(issuers.findByIssuer(issuer.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(issuer, URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
        var storage = new TianjiMemoryProperties(2, "0.8.0", null, 1, new VersionedMemoryEmbeddingModel.Profile("embedding", 1, "model", "text-embedding-3-small", 2, 8191, 64, 16), List.of(retention), List.of(new TianjiMemoryProperties.Origin(20, issuer)));
        var prefix = "stellar.tianji.agent.memory.extraction.";
        var runner = new ApplicationContextRunner().withUserConfiguration(TianjiMemoryPreferenceConfiguration.class).withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger)
                .withBean(PostgresMemoryService.class, () -> memory).withBean(TianjiMemoryProperties.class, () -> storage).withBean(TrustedIssuerRegistry.class, () -> issuers)
                .withBean("tianjiMemoryAuthorization", MemoryAuthorization.class, () -> request -> MemoryAuthorization.Decision.ALLOW)
                .withPropertyValues("stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.memory.http.enabled=true", prefix + "http.enabled=true",
                        prefix + "policies[0].key=preferences", prefix + "policies[0].version=1", prefix + "policies[0].preference-values.language[0]=zh-CN", prefix + "policies[0].candidate-confidence=0.5",
                        prefix + "policies[0].sensitivity=CONFIDENTIAL", prefix + "policies[0].retention-policy-ref=" + retention.reference(), prefix + "policies[0].maximum-candidates=3",
                        prefix + "bindings[0].id=source", prefix + "bindings[0].tenant-id=20", prefix + "bindings[0].issuer-fingerprint=" + actor.issuerFingerprint(), prefix + "bindings[0].definition-key=assistant",
                        prefix + "bindings[0].definition-version=1", prefix + "bindings[0].definition-sha256=" + "a".repeat(64), prefix + "bindings[0].scope-id=research", prefix + "bindings[0].policy-ref=" + policy.reference());
        runner.run(context -> {
            assertNull(context.getStartupFailure()); assertEquals(policy, context.getBean(TianjiMemoryPreferenceProperties.class).policies().getFirst());
            var admission = context.getBean(MemoryPreferenceAdmission.class); when(issuers.findByIssuer(issuer.toASCIIString())).thenReturn(Optional.empty());
            assertThrows(AgentLedgerException.class, () -> admission.extract(new MemoryOperationContext(actor, Instant.now().plusSeconds(30), null),
                    new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", "run", "terminal", "source", policy.reference())));
            verify(ledger, never()).executionMaterial(any(), any()); verify(memory, never()).upsert(any(), any()); verifyNoInteractions(source);
        });
        when(ledger.usesDataSource(source)).thenReturn(false); runner.run(context -> assertNotNull(context.getStartupFailure()));
    }
}
