package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration.ResumeBinding;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.stream.Collectors;

/** Opt-in protected checkpoint takeover with an independently provisioned issuance source. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "checkpoint-resume.api.enabled"}, havingValue = "true")
@Import(TianjiAgentCheckpointResumeConfiguration.class)
public class TianjiCheckpointResumeApiConfiguration {
    @Bean(name = "tianjiCheckpointResumeGrantHandoff")
    TianjiCheckpointResumeGrantHandoff tianjiCheckpointResumeGrantHandoff(
            @Qualifier("tianjiCheckpointResumeDeployment") Deployment deployment,
            IdpStarterProperties starter, IdentityAuthorityProperties ordinary,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper) {
        requireSeparateIssuance(starter, ordinary, deployment.issueAuthority(), issuers);
        return new TianjiCheckpointResumeGrantHandoff(starter, deployment.issueAuthority(), issuers, oauth, mapper, 32);
    }

    @Bean(name = "tianjiCheckpointResumeGrantAuthorizer", destroyMethod = "close")
    TianjiCheckpointResumeGrantAuthorizer tianjiCheckpointResumeGrantAuthorizer(
            @Qualifier("tianjiCheckpointResumeDeployment") Deployment deployment,
            @Qualifier("tianjiCheckpointResumeGrantHandoff") TianjiCheckpointResumeGrantHandoff handoff,
            IdpStarterProperties starter, Rbac3StarterProperties rbac,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper) {
        var clients = new LinkedHashMap<URI, HttpExecutionAuthorizationClient>();
        TianjiExecutionGrantAuthorizer.clients(starter, deployment.issueAuthority(), issuers, oauth, mapper, rbac,
                IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE, ExecutionAuthorizationContract.SERVICE_SCOPE)
                .values().forEach(client -> clients.put(client.issuer(), client.client()));
        return new TianjiCheckpointResumeGrantAuthorizer(starter.getResourceUri(), rbac.getSystemCode(),
                issuers, clients, handoff, Duration.ofSeconds(5), 2);
    }

    @Bean(name = "tianjiCheckpointResumeBinding")
    ResumeBinding tianjiCheckpointResumeBinding(@Qualifier("tianjiCheckpointResumeDeployment") Deployment deployment,
            @Qualifier("tianjiCheckpointResumeGrantAuthorizer") TianjiCheckpointResumeGrantAuthorizer authority) {
        return new ResumeBinding(deployment.recoverySource(), deployment.recoveryRole(), authority);
    }

    private static void requireSeparateIssuance(IdpStarterProperties starter, IdentityAuthorityProperties ordinary,
                                               IdentityAuthorityProperties resume, TrustedIssuerRegistry issuers) {
        if (ordinary == resume) throw invalid();
        var ordinaryRegistrations = TianjiExecutionAuthorityBindings.source(starter, ordinary, issuers).values().stream()
                .map(IdpServiceTokenRequest::registrationId).collect(Collectors.toUnmodifiableSet());
        var resumeSources = TianjiExecutionAuthorityBindings.source(starter, resume, issuers);
        if (resumeSources.isEmpty() || resumeSources.values().stream()
                .map(IdpServiceTokenRequest::registrationId).anyMatch(ordinaryRegistrations::contains)) throw invalid();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("TIANJI_CHECKPOINT_RESUME_ISSUANCE_MUST_BE_SEPARATE");
    }

    /** The operator provides the exact recovery pool and independent issuer registration bindings explicitly. */
    public record Deployment(DataSource recoverySource, String recoveryRole, IdentityAuthorityProperties issueAuthority) {
        public Deployment {
            Objects.requireNonNull(recoverySource, "recoverySource");
            Objects.requireNonNull(issueAuthority, "issueAuthority");
            if (recoveryRole == null || !recoveryRole.matches("[A-Za-z_][A-Za-z0-9_.-]{0,62}")) {
                throw new IllegalArgumentException("TIANJI_CHECKPOINT_RESUME_ROLE_INVALID");
            }
        }

        @Override public String toString() { return "CheckpointResumeDeployment[redacted]"; }
    }
}
