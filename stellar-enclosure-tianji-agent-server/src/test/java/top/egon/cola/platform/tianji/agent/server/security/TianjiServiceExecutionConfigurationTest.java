package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentServiceExecutionGrantStore;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiServiceExecutionConfigurationTest {
    @Test void service_execution_is_disabled_by_default_and_rejects_missing_prerequisites() {
        new ApplicationContextRunner().withUserConfiguration(TianjiServiceExecutionConfiguration.class)
                .run(context -> {
                    assertTrue(context.getBeansOfType(AgentServiceExecutionGrantStore.class).isEmpty());
                    assertTrue(context.getBeansOfType(TianjiServiceExecutionGrantIssuer.class).isEmpty());
                    assertTrue(context.getBeansOfType(TianjiServiceExecutionGrantAuthorizer.class).isEmpty());
                });
        var configuration = new TianjiServiceExecutionConfiguration();
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class);
        assertThrows(IllegalStateException.class, () -> configuration.agentServiceExecutionGrantStore(source, ledger, new MockEnvironment()));
        verifyNoInteractions(source, ledger);
        new ApplicationContextRunner().withUserConfiguration(TianjiServiceExecutionConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.service-execution.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test void enabling_service_execution_without_its_matching_authorizer_fails_closed() {
        @SuppressWarnings("unchecked") ObjectProvider<TianjiServiceExecutionGrantAuthorizer> services = mock(ObjectProvider.class);
        var environment = new MockEnvironment().withProperty("stellar.tianji.agent.execution-grants.enabled", "true")
                .withProperty("stellar.tianji.agent.service-execution.enabled", "true");
        var failure = assertThrows(IllegalStateException.class, () -> new TianjiExecutionAuthorizationConfiguration()
                .tianjiExecutionGrantAuthorizer(null, null, null, null, null, null, null, null, environment, services));
        assertEquals("TIANJI_SERVICE_EXECUTION_AUTHORIZER_REQUIRED", failure.getMessage());
    }

    @Test void enabled_components_share_the_same_verified_store_and_authorizer_without_starting_a_server() {
        URI issuer = URI.create("https://idp.example/tenants/21");
        URI resource = URI.create("https://api.example/tianji");
        var ledger = mock(AgentLedgerService.class); var userStore = mock(AgentExecutionGrantStore.class);
        var serviceStore = mock(AgentServiceExecutionGrantStore.class); var source = mock(DataSource.class);
        var issuers = mock(TrustedIssuerRegistry.class); var oauth = mock(IdpServiceOAuth2Client.class);
        when(userStore.usesLedger(ledger)).thenReturn(true); when(serviceStore.usesLedger(ledger)).thenReturn(true);
        when(issuers.findByIssuer(issuer.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(issuer,
                URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21",
                TrustedIssuerRegistry.Status.ACTIVE, 1)));
        when(oauth.authorizationKey(any())).thenAnswer(inv -> {
            IdpServiceTokenRequest value = inv.getArgument(0);
            return new ServiceAuthorizationKey(issuer.toASCIIString(), value.registrationId(), "agent", 1, value.appId(),
                    value.audience(), value.context(), value.tenantId(), value.scopes());
        });
        var starter = new top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties();
        starter.setResourceUri(resource); starter.getServiceClient().setAppId("agent-app");
        var authority = new top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties();
        authority.setBaseUri(URI.create("https://idp.example"));
        var sourceBinding = new top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties.Binding();
        sourceBinding.setIssuer(issuer); sourceBinding.setSourceResource(resource); sourceBinding.setRegistrationId("agent");
        sourceBinding.setAudience(URI.create("https://api.example/idp")); sourceBinding.setContext(ServiceTokenContext.TENANT);
        sourceBinding.setTenantId("21"); sourceBinding.setScopes(Set.of(IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE,
                IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE));
        authority.setBindings(Map.of("agent", sourceBinding));
        var rbac = new top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties();
        rbac.setSystemCode("tianji"); rbac.getAuthorization().setEndpoint("https://rbac.example");
        rbac.getAuthorization().getServiceToken().setResourceUri("https://api.example/rbac");
        new ApplicationContextRunner().withUserConfiguration(TestServiceConfiguration.class, TianjiExecutionAuthorizationConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.linked.enabled=true",
                        "stellar.tianji.agent.linked.submission-enabled=true", "stellar.tianji.agent.linked.bindings[0].issuer=" + issuer,
                        "stellar.tianji.agent.linked.bindings[0].tenant-id=21", "stellar.tianji.agent.linked.bindings[0].client-id=worker",
                        "stellar.tianji.agent.linked.bindings[0].app-id=process-app", "stellar.tianji.agent.linked.bindings[0].business-code=platform",
                        "stellar.tianji.agent.linked.bindings[0].application-code=process", "stellar.tianji.agent.linked.bindings[0].environment=test",
                        "stellar.tianji.agent.linked.bindings[0].source-id=process-east", "stellar.tianji.agent.linked.bindings[0].scope-types[0]=BPMN",
                        "stellar.tianji.agent.linked.bindings[0].definitions[0].key=review", "stellar.tianji.agent.linked.bindings[0].definitions[0].version=1",
                        "stellar.tianji.agent.linked.bindings[0].definitions[0].content-sha256=" + "a".repeat(64),
                        "stellar.tianji.agent.execution-grants.enabled=true", "stellar.tianji.agent.execution-authorization.enabled=true",
                        "stellar.tianji.agent.service-execution.enabled=true", "stellar.tianji.agent.service-execution.sources[0].issuer=" + issuer,
                        "stellar.tianji.agent.service-execution.sources[0].tenant-id=21", "stellar.tianji.agent.service-execution.sources[0].client-id=worker",
                        "stellar.tianji.agent.service-execution.sources[0].resource-uri=https://api.example/process")
                .withBean(TestFixtures.class, () -> new TestFixtures(serviceStore))
                .withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger)
                .withBean(AgentExecutionGrantStore.class, () -> userStore)
                .withBean("idpStarterProperties", top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties.class, () -> starter)
                .withBean("identityAuthorityProperties", top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties.class, () -> authority)
                .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, () -> issuers)
                .withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class, () -> oauth)
                .withBean("jacksonObjectMapper", com.fasterxml.jackson.databind.ObjectMapper.class, com.fasterxml.jackson.databind.ObjectMapper::new)
                .withBean(top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties.class, () -> rbac)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(TianjiLinkedServiceAdmission.class).size());
                    var authorizer = context.getBean(TianjiExecutionGrantAuthorizer.class);
                    assertTrue(authorizer.supportsService()); assertTrue(authorizer.usesLedger(ledger));
                    verify(serviceStore).requireSchema();
                });
        verify(oauth, never()).authorize(any()); verify(oauth, never()).authorizeBounded(any(), anyString(), any());
    }

    record TestFixtures(AgentServiceExecutionGrantStore store) { }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(TestFixtures.class)
    @org.springframework.boot.context.properties.EnableConfigurationProperties({TianjiServiceExecutionProperties.class, TianjiLinkedDispatchProperties.class})
    static class TestServiceConfiguration extends TianjiServiceExecutionConfiguration {
        private final TestFixtures fixtures;
        TestServiceConfiguration(TestFixtures fixtures) { this.fixtures = fixtures; }
        @Override @org.springframework.context.annotation.Bean
        AgentServiceExecutionGrantStore agentServiceExecutionGrantStore(DataSource source, AgentLedgerService ledger,
                                                                          org.springframework.core.env.Environment environment) {
            fixtures.store().requireSchema();
            return fixtures.store();
        }
    }
}
