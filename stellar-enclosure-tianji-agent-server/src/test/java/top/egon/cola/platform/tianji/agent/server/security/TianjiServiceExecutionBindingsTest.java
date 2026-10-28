package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiServiceExecutionBindingsTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenants/21");
    private static final URI SOURCE = URI.create("https://api.example/process");
    private static final String HASH = "a".repeat(64);
    private static final Instant DEADLINE = Instant.parse("2026-10-30T00:00:00Z");
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final TrustedIssuerRegistry.TrustedIssuerVO trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
            URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21",
            TrustedIssuerRegistry.Status.ACTIVE, 1);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", ISSUER.toASCIIString());
    private TianjiServiceExecutionBindings bindings;

    @BeforeEach void setUp() {
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        bindings = new TianjiServiceExecutionBindings(properties(), linked(), issuers);
    }

    @Test void reconstructs_only_the_configured_process_service_tuple() {
        var subject = bindings.require(request(actor, "review", 1, HASH), link("process-east", WorkerScopeTypeEnum.BPMN, HASH));
        assertEquals("worker", subject.clientId()); assertEquals("process-app", subject.appId());
        assertEquals("platform", subject.businessCode()); assertEquals("process", subject.applicationCode());
        assertEquals("test", subject.environment()); assertEquals(SOURCE, subject.resourceUri());
        assertFalse(subject.toString().contains("worker"));
    }

    @Test void actor_source_scope_and_definition_changes_fail_closed() {
        var link = link("process-east", WorkerScopeTypeEnum.BPMN, HASH);
        for (var actor : List.of(new TrustedAgentActor(21, TrustedAgentActor.SubjectType.SERVICE, "worker"),
                TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "worker", ISSUER.toASCIIString()),
                TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "other", ISSUER.toASCIIString()),
                TrustedAgentActor.verified(22, TrustedAgentActor.SubjectType.SERVICE, "worker", ISSUER.toASCIIString()),
                TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://other.example/tenant"))) {
            assertThrows(IllegalStateException.class, () -> bindings.require(request(actor, "review", 1, HASH), link));
        }
        assertThrows(IllegalStateException.class, () -> bindings.require(request(actor, "other", 1, HASH), link));
        assertThrows(IllegalStateException.class, () -> bindings.require(request(actor, "review", 2, HASH), link));
        assertThrows(IllegalStateException.class, () -> bindings.require(request(actor, "review", 1, "b".repeat(64)), link));
        assertThrows(IllegalStateException.class, () -> bindings.require(request(actor, "review", 1, HASH),
                link("other-source", WorkerScopeTypeEnum.BPMN, HASH)));
        assertThrows(IllegalStateException.class, () -> bindings.require(request(actor, "review", 1, HASH),
                link("process-east", WorkerScopeTypeEnum.CMMN, HASH)));
        assertThrows(IllegalStateException.class, () -> bindings.require(request(actor, "review", 1, HASH), null));
    }

    @Test void issuer_revocation_or_missing_source_configuration_blocks_execution() {
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> bindings.require(request(actor, "review", 1, HASH),
                link("process-east", WorkerScopeTypeEnum.BPMN, HASH)));
        assertThrows(IllegalStateException.class, () -> new TianjiServiceExecutionBindings(properties(), linked(), issuers));
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        var other = new TianjiServiceExecutionProperties(true, List.of(new TianjiServiceExecutionProperties.Source(
                ISSUER, 21, "other", SOURCE)));
        assertThrows(IllegalStateException.class, () -> new TianjiServiceExecutionBindings(other, linked(), issuers));
    }

    @Test void configuration_is_finite_explicit_and_binds_without_starting_services() {
        assertThrows(IllegalArgumentException.class, () -> new TianjiServiceExecutionProperties(true, List.of()));
        var source = properties().sources().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new TianjiServiceExecutionProperties(true, List.of(source, source)));
        assertThrows(IllegalArgumentException.class, () -> new TianjiServiceExecutionProperties.Source(ISSUER, 21, "worker", URI.create("http://api.example/process")));
        assertThrows(IllegalArgumentException.class, () -> new TianjiServiceExecutionProperties.Source(ISSUER, 21, "worker\n", SOURCE));
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.service-execution.enabled=true",
                        "stellar.tianji.agent.service-execution.sources[0].issuer=" + ISSUER,
                        "stellar.tianji.agent.service-execution.sources[0].tenant-id=21",
                        "stellar.tianji.agent.service-execution.sources[0].client-id=worker",
                        "stellar.tianji.agent.service-execution.sources[0].resource-uri=" + SOURCE)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(properties(), context.getBean(TianjiServiceExecutionProperties.class));
                });
    }

    @EnableConfigurationProperties(TianjiServiceExecutionProperties.class)
    static class PropertiesConfiguration { }

    private static TianjiServiceExecutionProperties properties() {
        return new TianjiServiceExecutionProperties(true, List.of(new TianjiServiceExecutionProperties.Source(ISSUER, 21, "worker", SOURCE)));
    }
    private static TianjiLinkedDispatchProperties linked() {
        return new TianjiLinkedDispatchProperties(true, true, List.of(new TianjiLinkedDispatchProperties.Binding(ISSUER, 21, "worker",
                "process-app", "platform", "process", "test", "process-east", Set.of(WorkerScopeTypeEnum.BPMN),
                List.of(new TianjiLinkedDispatchProperties.Definition("review", 1, HASH)))));
    }
    private static AgentRunAuthorization.Request request(TrustedAgentActor actor, String key, long version, String definitionHash) {
        return new AgentRunAuthorization.Request(actor, "00000000-0000-0000-0000-000000000123", key, version,
                definitionHash, "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE);
    }
    private static AgentProcessLink link(String source, WorkerScopeTypeEnum type, String definitionHash) {
        return new AgentProcessLink(source, type, "process-instance", "activity-occurrence", definitionHash, DEADLINE);
    }
}
