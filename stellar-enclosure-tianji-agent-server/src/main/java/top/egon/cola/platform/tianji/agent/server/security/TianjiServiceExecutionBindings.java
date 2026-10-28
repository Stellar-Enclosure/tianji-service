package top.egon.cola.platform.tianji.agent.server.security;

import java.util.Objects;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

/** Reconstructs a configured SERVICE tuple from the immutable process link and current trust. */
public final class TianjiServiceExecutionBindings {
    private final TianjiServiceExecutionProperties properties;
    private final TianjiLinkedDispatchProperties linked;
    private final TrustedIssuerRegistry issuers;

    public TianjiServiceExecutionBindings(TianjiServiceExecutionProperties properties, TianjiLinkedDispatchProperties linked,
                                          TrustedIssuerRegistry issuers) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.linked = Objects.requireNonNull(linked, "linked");
        this.issuers = Objects.requireNonNull(issuers, "issuers");
        if (!properties.enabled() || properties.sources().isEmpty() || !linked.enabled() || linked.bindings().isEmpty()) throw unavailable();
        for (var source : properties.sources()) {
            requireTrust(source);
            if (linked.bindings().stream().noneMatch(binding -> sameSource(source, binding))) throw unavailable();
        }
    }

    public AgentServiceExecutionGrant.Subject require(AgentRunAuthorization.Request request, AgentProcessLink link) {
        if (request == null || link == null || request.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE
                || "LEGACY".equals(request.actor().issuerFingerprint())
                || !request.definitionSha256().equals(link.definitionContentSha256())) throw unavailable();
        var actor = request.actor();
        var matches = linked.bindings().stream().filter(binding -> binding.tenantId() == actor.tenantId()
                && binding.clientId().equals(actor.subjectId())
                && TrustedIssuerRegistry.issuerHash(binding.issuer()).equals(actor.issuerFingerprint())
                && binding.sourceId().equals(link.sourceId()) && binding.scopeTypes().contains(link.scopeType())
                && binding.definitions().stream().anyMatch(definition -> definition.key().equals(request.definitionKey())
                && definition.version() == request.definitionVersion() && definition.contentSha256().equals(request.definitionSha256())))
                .toList();
        if (matches.size() != 1) throw unavailable();
        var binding = matches.getFirst();
        var sources = properties.sources().stream().filter(source -> sameSource(source, binding)).toList();
        if (sources.size() != 1) throw unavailable();
        var source = sources.getFirst();
        requireTrust(source);
        return new AgentServiceExecutionGrant.Subject(binding.clientId(), binding.appId(), binding.businessCode(),
                binding.applicationCode(), binding.environment(), source.resourceUri());
    }

    boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate; }

    private static boolean sameSource(TianjiServiceExecutionProperties.Source source, TianjiLinkedDispatchProperties.Binding binding) {
        return source.issuer().toASCIIString().equals(binding.issuer().toASCIIString())
                && source.tenantId() == binding.tenantId() && source.clientId().equals(binding.clientId());
    }

    private void requireTrust(TianjiServiceExecutionProperties.Source source) {
        var trusted = issuers.findByIssuer(source.issuer().toASCIIString()).orElseThrow(TianjiServiceExecutionBindings::unavailable);
        if (trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(source.tenantId()).equals(trusted.tenantId())) throw unavailable();
    }

    private static IllegalStateException unavailable() { return new IllegalStateException("TIANJI_SERVICE_EXECUTION_BINDING_UNAVAILABLE"); }
}
