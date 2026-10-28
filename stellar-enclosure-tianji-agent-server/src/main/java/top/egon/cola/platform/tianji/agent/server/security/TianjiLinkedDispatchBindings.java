package top.egon.cola.platform.tianji.agent.server.security;

import java.util.Objects;
import java.time.Clock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

/** Rechecks the exact verified SERVICE tuple, tenant and configured process source on every request. */
public final class TianjiLinkedDispatchBindings {
    private final TianjiLinkedDispatchProperties properties;
    private final TrustedIssuerRegistry issuers;
    private final Clock clock;

    public TianjiLinkedDispatchBindings(TianjiLinkedDispatchProperties properties, TrustedIssuerRegistry issuers, Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (!properties.enabled() || properties.bindings().isEmpty()) throw new IllegalStateException("TIANJI_LINKED_BINDINGS_REQUIRED");
        properties.bindings().forEach(this::requireTrustedIssuer);
    }

    public Bound require(TianjiAgentAccess.Authorized authorized, AgentProcessLink link, LinkedAgentStartCommand submission) {
        if (authorized == null || authorized.actor() == null || authorized.issuer() == null || link == null
                || authorized.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE
                || !(SecurityContextHolder.getContext().getAuthentication() instanceof IdpAuthenticationToken token)
                || !token.isAuthenticated() || !(token.getPrincipal() instanceof ServiceIdentityPrincipal principal)
                || !authorized.issuer().equals(issuer(token)) || !authorized.actor().subjectId().equals(principal.subject())
                || !Long.toString(authorized.actor().tenantId()).equals(principal.tenantId())
                || principal.scopeContext() != ServiceTokenContext.TENANT || !principal.expiresAt().equals(authorized.expiresAt())
                || !principal.expiresAt().isAfter(clock.instant()) || submission != null && !link.equals(submission.link())) throw denied();
        if (!TrustedAgentActor.verified(authorized.actor().tenantId(), TrustedAgentActor.SubjectType.SERVICE,
                principal.subject(), authorized.issuer().toString()).equals(authorized.actor())) throw denied();
        var binding = properties.bindings().stream().filter(value -> value.issuer().equals(authorized.issuer())
                && value.tenantId() == authorized.actor().tenantId() && value.clientId().equals(principal.clientId())
                && value.appId().equals(principal.appId()) && value.businessCode().equals(principal.sourceBizCode())
                && value.applicationCode().equals(principal.sourceAppCode()) && value.environment().equals(principal.sourceEnvironment())
                && value.sourceId().equals(link.sourceId()) && value.scopeTypes().contains(link.scopeType())).findFirst().orElseThrow(TianjiLinkedDispatchBindings::denied);
        requireTrustedIssuer(binding);
        if (submission != null && binding.definitions().stream().noneMatch(definition ->
                definition.key().equals(submission.start().definitionKey()) && definition.version() == submission.start().definitionVersion()
                        && definition.contentSha256().equals(link.definitionContentSha256()))) throw denied();
        return new Bound(binding, principal);
    }

    private void requireTrustedIssuer(TianjiLinkedDispatchProperties.Binding binding) {
        var trusted = issuers.findByIssuer(binding.issuer().toASCIIString()).orElseThrow(TianjiLinkedDispatchBindings::denied);
        if (trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(binding.tenantId()).equals(trusted.tenantId())) throw denied();
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("Linked service binding denied"); }
    private static java.net.URI issuer(IdpAuthenticationToken token) {
        try { return token.verifiedIssuer(); }
        catch (IllegalStateException missing) { throw denied(); }
    }
    public record Bound(TianjiLinkedDispatchProperties.Binding binding, ServiceIdentityPrincipal principal) {
        @Override public String toString() { return "TianjiLinkedDispatchBindings.Bound[redacted]"; }
    }
}
