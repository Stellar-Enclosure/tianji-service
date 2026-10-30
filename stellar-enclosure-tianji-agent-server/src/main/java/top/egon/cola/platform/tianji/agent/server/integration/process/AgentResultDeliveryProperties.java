package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Destination and credential references are operator-owned; payloads contain neither URLs nor secrets. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.result-delivery")
public record AgentResultDeliveryProperties(List<Destination> destinations) {
    public AgentResultDeliveryProperties {
        destinations = destinations == null ? List.of() : List.copyOf(destinations);
        if (destinations.size() > 1000) throw invalid();
        Set<List<Object>> keys = new HashSet<>();
        for (var destination : destinations) if (destination == null || !keys.add(List.of(destination.id(), destination.tenantId()))) throw invalid();
    }

    public record Destination(String id, long tenantId, URI baseUri, URI resourceUri, URI issuer, String registrationId,
                               Duration timeout, int maxTokenCalls, List<Owner> owners) {
        public Destination {
            text(id); text(registrationId);
            uri(baseUri); uri(resourceUri); uri(issuer);
            timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
            maxTokenCalls = maxTokenCalls == 0 ? 4 : maxTokenCalls;
            owners = owners == null ? List.of() : List.copyOf(owners);
            if (tenantId <= 0 || resourceUri.toASCIIString().length() > 512 || timeout.compareTo(Duration.ofMillis(100)) < 0
                    || timeout.compareTo(Duration.ofSeconds(30)) > 0 || maxTokenCalls < 1 || maxTokenCalls > 8 || owners.isEmpty() || owners.size() > 1000) throw invalid();
            Set<List<Object>> identities = new HashSet<>();
            for (Owner owner : owners) {
                if (owner == null || !identities.add(List.of(owner.actor(tenantId), owner.sourceId()))) throw invalid();
            }
        }
    }

    public record Owner(URI issuer, String subjectId, String sourceId) {
        public Owner { uri(issuer); text(subjectId); text(sourceId); }
        TrustedAgentActor actor(long tenantId) { return TrustedAgentActor.verified(tenantId, TrustedAgentActor.SubjectType.SERVICE, subjectId, issuer.toASCIIString()); }
    }

    private static void text(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid(); }
    private static void uri(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null || !value.normalize().equals(value)
                || !value.toASCIIString().equals(value.toString()) || value.toASCIIString().length() > 2048) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent result delivery configuration is invalid"); }
}
