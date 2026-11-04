package top.egon.cola.platform.tianji.process.server.bridge;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Finite local SERVICE identity metadata. No token, remote authorization or historical USER context is requested. */
public final class CaseSourceRegistry {
    private static final Set<String> SCOPES = Set.of("tianji:case:start");
    private static final Set<String> WITHDRAWN = Set.of("registration_identity_unavailable", "app_id_mismatch", "registration_invalid", "issuer_untrusted", "issuer_unconfigured", "issuer_context_mismatch", "token_endpoint_invalid", "token_endpoint_untrusted", "credential_version_unconfigured");
    private final Map<BindingKey, Registration> byBinding;
    private final Map<CaseCompletionInbox.RegistrationKey, Registration> byInbox;
    private final List<Registration> registrations;
    private final IdpStarterProperties starter;
    private final TrustedIssuerRegistry issuers;
    private final IdpServiceOAuth2Client oauth;

    public CaseSourceRegistry(CaseSourceProperties properties, IdpStarterProperties starter, TrustedIssuerRegistry issuers, Supplier<IdpServiceOAuth2Client> oauth) {
        Objects.requireNonNull(properties, "properties");
        if (properties.sources().isEmpty()) { byBinding = Map.of(); byInbox = Map.of(); registrations = List.of(); this.starter = null; this.issuers = null; this.oauth = null; return; }
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Case source setup must precede native transactions");
        this.starter = Objects.requireNonNull(starter, "starter"); this.issuers = Objects.requireNonNull(issuers, "issuers"); this.oauth = Objects.requireNonNull(Objects.requireNonNull(oauth, "oauth").get(), "oauth");
        var bindings = new LinkedHashMap<BindingKey, Registration>(); var inboxes = new LinkedHashMap<CaseCompletionInbox.RegistrationKey, Registration>();
        var sourceIds = new LinkedHashSet<List<Object>>();
        for (var source : properties.sources()) {
            if (!sourceIds.add(List.of(source.tenantId(), source.sourceId())) || starter.getServiceClient() == null || !source.appId().equals(starter.getServiceClient().getAppId())) throw invalid();
            var actor = ProcessActor.verified(source.tenantId(), ProcessActor.SubjectType.SERVICE, source.serviceSubject(), source.issuer().toASCIIString());
            if (!trusted(actor, source.issuer().toASCIIString())) throw invalid();
            var request = new IdpServiceTokenRequest(source.registrationId(), source.appId(), source.resourceUri(), ServiceTokenContext.TENANT, Long.toString(source.tenantId()), SCOPES);
            var key = this.oauth.authorizationKey(request);
            if (key == null || !source.issuer().toASCIIString().equals(key.issuer()) || !matches(key, request, actor)) throw invalid();
            var registration = new Registration(source.binding(), source.sourceId(), actor, key);
            if (bindings.putIfAbsent(registration.binding(), registration) != null || inboxes.putIfAbsent(registration.inboxKey(), registration) != null) throw invalid();
        }
        byBinding = Map.copyOf(bindings); byInbox = Map.copyOf(inboxes); registrations = List.copyOf(bindings.values());
    }
    public Registration require(BindingKey binding) {
        var registration = byBinding.get(binding);
        if (registration == null || !current(registration)) throw new IllegalStateException("Case source registration is unavailable"); return registration;
    }
    /** False is an explicit withdrawal/mismatch; unexpected local failures remain failures for bounded retry. */
    public boolean current(Registration registration) {
        if (registration == null || !registration.equals(byBinding.get(registration.binding()))) return false;
        var expected = registration.authorizationKey();
        if (!trusted(registration.actor(), expected.issuer()) || starter.getServiceClient() == null || !expected.appId().equals(starter.getServiceClient().getAppId())) return false;
        try {
            var request = new IdpServiceTokenRequest(expected.registrationId(), expected.appId(), expected.audience(), ServiceTokenContext.TENANT, Long.toString(registration.actor().tenantId()), SCOPES);
            return expected.equals(oauth.authorizationKey(request));
        } catch (IdpServiceOAuth2Client.IdpServiceAuthorizationException withdrawn) {
            if (WITHDRAWN.contains(withdrawn.errorCode())) return false; throw withdrawn;
        }
    }
    public List<Registration> registrations() { return registrations; }
    public Optional<Registration> find(CaseCompletionInbox.RegistrationKey key) { return Optional.ofNullable(byInbox.get(key)); }
    public Set<CaseCompletionInbox.RegistrationKey> activeDueKeys() {
        var result = new LinkedHashSet<CaseCompletionInbox.RegistrationKey>(); for (var registration : registrations) if (current(registration)) result.add(registration.inboxKey()); return Set.copyOf(result);
    }
    private boolean trusted(ProcessActor actor, String issuer) {
        var trusted = issuers.findByIssuer(issuer).orElse(null);
        return trusted != null && trusted.status() == TrustedIssuerRegistry.Status.ACTIVE && trusted.realmType() == TrustedIssuerRegistry.RealmType.TENANT
                && Long.toString(actor.tenantId()).equals(trusted.tenantId()) && issuer.equals(trusted.issuer().toASCIIString());
    }
    private static boolean matches(ServiceAuthorizationKey key, IdpServiceTokenRequest request, ProcessActor actor) {
        return key != null && key.context() == ServiceTokenContext.TENANT && key.clientId().equals(actor.subjectId())
                && ProcessActor.verified(actor.tenantId(), ProcessActor.SubjectType.SERVICE, key.clientId(), key.issuer()).equals(actor)
                && key.registrationId().equals(request.registrationId()) && key.appId().equals(request.appId()) && key.audience().equals(request.audience())
                && key.tenantId().equals(request.tenantId()) && key.scopes().equals(SCOPES);
    }
    public record BindingKey(long tenantId, String releaseKey, String parentDefinitionKey, String parentModelSha256, String sourceElementId, String caseDefinitionKey, String caseModelSha256) {
        public BindingKey {
            if (tenantId < 1 || releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw invalid();
            definition(parentDefinitionKey); definition(sourceElementId); definition(caseDefinitionKey); hash(parentModelSha256); hash(caseModelSha256);
        }
        @Override public String toString() { return "CaseSourceRegistry.BindingKey[redacted]"; }
    }
    public record Registration(BindingKey binding, String sourceId, ProcessActor actor, ServiceAuthorizationKey authorizationKey) {
        public Registration {
            if (binding == null || sourceId == null || !sourceId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || actor == null || actor.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())
                    || actor.tenantId() != binding.tenantId() || authorizationKey == null || !authorizationKey.clientId().equals(actor.subjectId())
                    || !ProcessActor.verified(actor.tenantId(), ProcessActor.SubjectType.SERVICE, actor.subjectId(), authorizationKey.issuer()).equals(actor)
                    || authorizationKey.context() != ServiceTokenContext.TENANT || !Long.toString(actor.tenantId()).equals(authorizationKey.tenantId()) || !authorizationKey.scopes().equals(SCOPES)) throw invalid();
        }
        public CaseCompletionInbox.RegistrationKey inboxKey() { return new CaseCompletionInbox.RegistrationKey(actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), sourceId); }
        @Override public String toString() { return "CaseSourceRegistry.Registration[redacted]"; }
    }
    private static void definition(String value) { if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) throw invalid(); }
    private static void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case source registration is invalid"); }
}
