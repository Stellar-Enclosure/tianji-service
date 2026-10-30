package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.platform.tianji.client.HttpAgentResultClient;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultClient;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiAgentResultDeliveryConfigurationTest {
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final URI RESOURCE = URI.create("https://api.example/process");
    private final TianjiAgentResultDeliveryConfiguration configuration = new TianjiAgentResultDeliveryConfiguration();

    @Test
    void delivery_is_absent_by_default_and_cannot_be_enabled_without_publication_storage() {
        new ApplicationContextRunner().withUserConfiguration(TianjiAgentResultDeliveryConfiguration.class).run(context -> assertNull(context.getStartupFailure()));
        new ApplicationContextRunner().withUserConfiguration(TianjiAgentResultDeliveryConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.result-delivery.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
        assertThrows(IllegalArgumentException.class, () -> new AgentResultDeliveryProperties.Destination("process-one", 20,
                URI.create("http://remote.example"), RESOURCE, ISSUER, "process-client", Duration.ofSeconds(3), 4, owners()));
    }

    @Test
    void explicit_tenant_resource_issuer_and_receiver_scope_are_the_only_token_context() throws Exception {
        var starter = new IdpStarterProperties(); starter.getServiceClient().setAppId("agent-app");
        var registry = registry();
        var oauth = mock(IdpServiceOAuth2Client.class);
        var now = Instant.now();
        when(oauth.authorizeBounded(any(), eq(ISSUER.toASCIIString()), eq(Duration.ofSeconds(3))))
                .thenReturn(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token", now, now.plusSeconds(60), Set.of(AgentResultClient.RECEIVE_SCOPE)));
        var captured = new AtomicReference<HttpAgentResultClient.TokenProvider>();
        try (var construction = mockConstruction(HttpAgentResultClient.class, (client, context) -> {
            captured.set((HttpAgentResultClient.TokenProvider) context.arguments().get(2));
            when(client.timeout()).thenReturn((Duration) context.arguments().get(3));
        }); var clients = configuration.tianjiAgentResultDeliveryClients(properties(Duration.ofSeconds(3)), starter, registry, oauth, new TransactionalOutboxProperties())) {
            assertEquals(1, clients.routes().size());
            assertEquals("service-token", captured.get().acquire(20));
            captured.get().rejected(20);
            assertThrows(IllegalStateException.class, () -> captured.get().acquire(21));
            var expected = new IdpServiceTokenRequest("process-client", "agent-app", RESOURCE, ServiceTokenContext.TENANT, "20", Set.of(AgentResultClient.RECEIVE_SCOPE));
            verify(oauth).authorizeBounded(expected, ISSUER.toASCIIString(), Duration.ofSeconds(3));
            verify(oauth).invalidateBounded(expected, ISSUER.toASCIIString(), Duration.ofSeconds(3));
            verify(oauth, never()).authorize(any());
        }
    }

    @Test
    void issuer_realm_or_tenant_and_insufficient_delivery_budget_fail_before_authorization() {
        var starter = new IdpStarterProperties(); starter.getServiceClient().setAppId("agent-app");
        var oauth = mock(IdpServiceOAuth2Client.class);
        var registry = registry();
        assertThrows(IllegalStateException.class, () -> configuration.tianjiAgentResultDeliveryClients(properties(Duration.ofSeconds(9)), starter,
                registry, oauth, new TransactionalOutboxProperties()));
        when(registry.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
                URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21", TrustedIssuerRegistry.Status.ACTIVE, 1)));
        assertThrows(IllegalStateException.class, () -> configuration.tianjiAgentResultDeliveryClients(properties(Duration.ofSeconds(3)), starter,
                registry, oauth, new TransactionalOutboxProperties()));
        verifyNoInteractions(oauth);
    }

    private static List<AgentResultDeliveryProperties.Owner> owners() {
        return List.of(new AgentResultDeliveryProperties.Owner(ISSUER, "process-worker", "process-east"));
    }
    private static AgentResultDeliveryProperties properties(Duration timeout) {
        return new AgentResultDeliveryProperties(List.of(new AgentResultDeliveryProperties.Destination("process-one", 20,
                URI.create("https://process.example/"), RESOURCE, ISSUER, "process-client", timeout, 4, owners())));
    }
    private static TrustedIssuerRegistry registry() {
        var registry = mock(TrustedIssuerRegistry.class);
        when(registry.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
                URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
        return registry;
    }
}
