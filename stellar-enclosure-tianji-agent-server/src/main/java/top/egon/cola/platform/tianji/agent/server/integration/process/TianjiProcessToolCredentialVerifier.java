package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianquan.shoubing.contract.PrincipalType;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Shared identity verification only; each transport owns its original window and request-local bearer. */
public final class TianjiProcessToolCredentialVerifier {
    private final IdpServiceOAuth2Client oauth;
    private final TenantIssuerAuthenticationManagerComponent authentication;
    private final VerifiedPrincipalConverter converter;
    private final TrustedIssuerRegistry issuers;
    private final Clock clock;

    public TianjiProcessToolCredentialVerifier(IdpServiceOAuth2Client oauth, TenantIssuerAuthenticationManagerComponent authentication,
            VerifiedPrincipalConverter converter, TrustedIssuerRegistry issuers, Clock clock) {
        this.oauth = Objects.requireNonNull(oauth, "oauth"); this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.converter = Objects.requireNonNull(converter, "converter"); this.issuers = Objects.requireNonNull(issuers, "issuers"); this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CredentialVO credentials(TargetVO target, Instant deadlineAt, LongSupplier remainingNanos, Consumer<String> publishBearer, Supplier<String> currentBearer) {
        remainingNanos.getAsLong();
        var request = new IdpServiceTokenRequest(target.registrationId(), target.expectedAppId(), target.resource(), ServiceTokenContext.TENANT, Long.toString(target.tenantId()), target.scopes());
        var trust = trust(target); var key = oauth.authorizationKey(request); requireKey(key, request, target); remainingNanos.getAsLong();
        var token = oauth.authorizeBounded(request, target.issuer().toASCIIString(), Duration.ofNanos(remainingNanos.getAsLong()));
        remainingNanos.getAsLong();
        if (token == null || !OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType()) || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(clock.instant())) throw unavailable();
        publishBearer.accept(token.getTokenValue());
        var proof = authentication.authenticate(currentBearer.get(), target.resource()); remainingNanos.getAsLong(); proof.validate();
        var jwt = proof.getJwt(); var principal = converter.toServicePrincipal(jwt, target.resource()); remainingNanos.getAsLong();
        String tenant = Long.toString(target.tenantId());
        if (proof.getPrincipalType() != PrincipalType.SERVICE || !sameTrust(trust, proof.getTrustedIssuer()) || !Objects.equals(currentBearer.get(), jwt.getTokenValue())
                || !target.issuer().toASCIIString().equals(jwt.getClaimAsString("iss")) || !List.of(target.resource().toASCIIString()).equals(jwt.getAudience())
                || !target.expectedClientId().equals(proof.getSubject()) || !tenant.equals(proof.getTenantId()) || !target.expectedClientId().equals(jwt.getSubject())
                || !tenant.equals(jwt.getClaimAsString("tenant_id")) || !proof.getTokenId().equals(jwt.getClaimAsString("jti"))
                || principal == null || !tenant.equals(principal.tenantId()) || !target.expectedClientId().equals(principal.subject())
                || !key.clientId().equals(principal.clientId()) || !target.expectedClientId().equals(principal.clientId()) || !target.expectedAppId().equals(principal.appId())
                || principal.scopeContext() != ServiceTokenContext.TENANT || !target.resource().toASCIIString().equals(principal.resourceUri().toASCIIString())
                || !target.expectedSourceBiz().equals(principal.sourceBizCode()) || !target.expectedSourceApp().equals(principal.sourceAppCode())
                || !target.expectedEnvironment().equals(principal.sourceEnvironment()) || !target.scopes().equals(principal.scopes())
                || !proof.getTokenId().equals(principal.tokenId()) || !principal.issuedAt().equals(jwt.getIssuedAt()) || !principal.expiresAt().equals(jwt.getExpiresAt())
                || !proof.getExpiresAt().equals(jwt.getExpiresAt())) throw unavailable();
        Instant expiry = earlier(earlier(token.getExpiresAt(), principal.expiresAt()), deadlineAt);
        var actor = TrustedAgentActor.verified(target.tenantId(), TrustedAgentActor.SubjectType.SERVICE, principal.clientId(), proof.getTrustedIssuer().issuer().toASCIIString());
        return new CredentialVO(request, key, trust, actor, expiry);
    }

    public void requireCurrent(TargetVO target, CredentialVO credential) {
        var key = oauth.authorizationKey(credential.request()); requireKey(key, credential.request(), target);
        if (!sameKey(credential.key(), key) || !sameTrust(credential.trust(), trust(target))) throw unavailable();
    }

    private TrustedIssuerRegistry.TrustedIssuerVO trust(TargetVO target) {
        var trust = issuers.findByIssuer(target.issuer().toASCIIString()).orElseThrow(() -> unavailable());
        if (trust.status() != TrustedIssuerRegistry.Status.ACTIVE || trust.realmType() != TrustedIssuerRegistry.RealmType.TENANT || !Long.toString(target.tenantId()).equals(trust.tenantId())
                || !target.issuer().toASCIIString().equals(trust.issuer().toASCIIString())) throw unavailable();
        return trust;
    }
    private static void requireKey(ServiceAuthorizationKey key, IdpServiceTokenRequest request, TargetVO target) {
        if (key == null || !target.issuer().toASCIIString().equals(key.issuer()) || !target.registrationId().equals(key.registrationId())
                || !target.expectedClientId().equals(key.clientId()) || !target.expectedAppId().equals(key.appId())
                || !target.resource().toASCIIString().equals(key.audience().toASCIIString()) || key.context() != ServiceTokenContext.TENANT
                || !Long.toString(target.tenantId()).equals(key.tenantId()) || !target.scopes().equals(key.scopes()) || !request.scopes().equals(key.scopes())) throw unavailable();
    }
    private static boolean sameKey(ServiceAuthorizationKey expected, ServiceAuthorizationKey current) {
        return expected.equals(current) && expected.audience().toASCIIString().equals(current.audience().toASCIIString());
    }
    private static boolean sameTrust(TrustedIssuerRegistry.TrustedIssuerVO expected, TrustedIssuerRegistry.TrustedIssuerVO current) {
        return current != null && expected.equals(current) && expected.issuer().toASCIIString().equals(current.issuer().toASCIIString()) && expected.jwksUri().toASCIIString().equals(current.jwksUri().toASCIIString());
    }

    public record TargetVO(long tenantId, URI issuer, URI resource, String registrationId, String expectedClientId, String expectedAppId,
                    String expectedSourceBiz, String expectedSourceApp, String expectedEnvironment, Set<String> scopes) {
        public TargetVO { scopes = Set.copyOf(scopes); }
        @Override public String toString() { return "ProcessToolCredentialTarget[redacted]"; }
    }
    public record CredentialVO(IdpServiceTokenRequest request, ServiceAuthorizationKey key, TrustedIssuerRegistry.TrustedIssuerVO trust,
                        TrustedAgentActor actor, Instant expiresAt) {
        @Override public String toString() { return "ProcessToolCredentialContext[redacted]"; }
    }
    private static Instant earlier(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
    private static IllegalStateException unavailable() { return new IllegalStateException("Process tool credentials are unavailable"); }
}
