package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.client.HttpAgentResultClient;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultClient;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Separately enables authenticated delivery; neither credentials nor tokens are written into Outbox records. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.result-delivery", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentResultDeliveryProperties.class)
public class TianjiAgentResultDeliveryConfiguration {
    @Bean
    static BeanFactoryPostProcessor resultDeliveryPrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.agent.terminal-events.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Agent result delivery requires terminal publication and owning storage");
            }
        };
    }

    @Bean(destroyMethod = "close")
    ResultDeliveryClients tianjiAgentResultDeliveryClients(AgentResultDeliveryProperties properties, IdpStarterProperties starter,
                                                          TrustedIssuerRegistry issuers, IdpServiceOAuth2Client oauth,
                                                          TransactionalOutboxProperties outbox) {
        if (properties.destinations().isEmpty() || starter.getServiceClient() == null) throw invalid();
        for (var destination : properties.destinations()) {
            requireTrustedDestination(issuers, destination);
            if (outbox.getDelivery().getTimeout() == null
                    || outbox.getDelivery().getTimeout().compareTo(destination.timeout().plusSeconds(1).plusMillis(100)) < 0) throw invalid();
        }
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        List<HttpAgentResultClient> clients = new ArrayList<>();
        List<AgentResultDeliveryHandler.Route> routes = new ArrayList<>();
        try {
            for (var destination : properties.destinations()) {
                var context = new IdpServiceTokenRequest(destination.registrationId(), starter.getServiceClient().getAppId(), destination.resourceUri(),
                        ServiceTokenContext.TENANT, Long.toString(destination.tenantId()), Set.of(AgentResultClient.RECEIVE_SCOPE));
                var tokens = new HttpAgentResultClient.TokenProvider() {
                    private void tenant(long requested) { if (requested != destination.tenantId()) throw invalid(); }
                    @Override public String acquire(long tenantId) {
                        tenant(tenantId);
                        return oauth.authorizeBounded(context, destination.issuer().toASCIIString(), destination.timeout()).getTokenValue();
                    }
                    @Override public void rejected(long tenantId) {
                        tenant(tenantId);
                        oauth.invalidateBounded(context, destination.issuer().toASCIIString(), destination.timeout());
                    }
                };
                var client = new HttpAgentResultClient(destination.baseUri(), http, tokens, destination.timeout(), destination.maxTokenCalls());
                clients.add(client);
                for (var owner : destination.owners()) {
                    routes.add(new AgentResultDeliveryHandler.Route(destination.id(), owner.sourceId(), owner.actor(destination.tenantId()),
                            client, () -> isTrustedDestination(issuers, destination)));
                }
            }
            return new ResultDeliveryClients(http, clients, routes);
        } catch (RuntimeException failure) {
            clients.forEach(HttpAgentResultClient::close); http.shutdownNow(); throw failure;
        }
    }

    @Bean
    AgentResultDeliveryHandler tianjiAgentResultDeliveryHandler(ObjectProvider<AgentLedgerService> ledgers, ResultDeliveryClients clients,
                                                                Environment environment, ObjectProvider<OpenTelemetry> telemetry,
                                                                ObjectProvider<MeterRegistry> meters,
                                                                @Qualifier(TianjiAgentResultArtifactConfiguration.VERIFIER)
                                                                ObjectProvider<AgentResultArtifactVerifier> artifacts) {
        var observation = environment.getProperty(AgentResultSpanObservation.FLAG, Boolean.class, false)
                ? AgentResultSpanObservation.enabled(telemetry::getIfUnique, meters::getIfUnique)
                : AgentResultSpanObservation.disabled();
        boolean crossProcess = observation.isEnabled()
                && environment.getProperty(AgentResultSpanObservation.CROSS_PROCESS_FLAG, Boolean.class, false);
        return new AgentResultDeliveryHandler(ledgers::getObject, clients.routes(), Clock.systemUTC(), observation, crossProcess, artifacts.getIfAvailable());
    }

    private static void requireTrustedDestination(TrustedIssuerRegistry issuers, AgentResultDeliveryProperties.Destination destination) {
        if (!isTrustedDestination(issuers, destination)) throw invalid();
    }

    private static boolean isTrustedDestination(TrustedIssuerRegistry issuers, AgentResultDeliveryProperties.Destination destination) {
        return issuers.findByIssuer(destination.issuer().toASCIIString())
                .filter(trusted -> trusted.realmType() == TrustedIssuerRegistry.RealmType.TENANT
                        && trusted.status() == TrustedIssuerRegistry.Status.ACTIVE
                        && Long.toString(destination.tenantId()).equals(trusted.tenantId()))
                .isPresent();
    }

    static final class ResultDeliveryClients implements AutoCloseable {
        private final HttpClient http;
        private final List<HttpAgentResultClient> clients;
        private final List<AgentResultDeliveryHandler.Route> routes;
        ResultDeliveryClients(HttpClient http, List<HttpAgentResultClient> clients, List<AgentResultDeliveryHandler.Route> routes) {
            this.http = http; this.clients = List.copyOf(clients); this.routes = List.copyOf(routes);
        }
        List<AgentResultDeliveryHandler.Route> routes() { return routes; }
        @Override public void close() { clients.forEach(HttpAgentResultClient::close); http.shutdownNow(); }
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Agent result SERVICE delivery bindings are invalid"); }
}
