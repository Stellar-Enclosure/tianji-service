package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration.ResumeHost;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import javax.sql.DataSource;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiCheckpointResumeApiConfigurationTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenant/7");
    private static final URI RESOURCE = URI.create("https://tianji.example");
    private static final URI ISSUER_AUDIENCE = URI.create("https://identity-authority.example");
    private static final String API = "stellar.tianji.agent.api.enabled=true";
    private static final String RESUME = "stellar.tianji.agent.checkpoint-resume.api.enabled=true";

    @Test
    void default_and_single_flag_contexts_have_no_resume_host_or_clients() {
        var runner = new WebApplicationContextRunner().withUserConfiguration(TianjiCheckpointResumeApiConfiguration.class);
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ResumeHost.class)
                .doesNotHaveBean(TianjiCheckpointResumeGrantHandoff.class));
        runner.withPropertyValues(API).run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(TianjiCheckpointResumeGrantAuthorizer.class));
        runner.withPropertyValues(RESUME).run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ResumeHost.class));
    }

    @Test
    void enabled_api_requires_an_explicit_named_deployment() {
        new WebApplicationContextRunner().withUserConfiguration(TianjiCheckpointResumeApiConfiguration.class)
                .withPropertyValues(API, RESUME)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("tianjiCheckpointResumeDeployment");
                });
    }

    @Test
    void refuses_reused_start_registration_before_building_issuance_clients() {
        var fixture = new Fixture();
        var ordinary = authority("existing-start");
        var resume = authority("existing-start");
        try (var clients = mockConstruction(HttpExecutionGrantClient.class)) {
            var failure = assertThrows(IllegalStateException.class, () -> fixture.handoff(ordinary, resume));
            assertEquals("TIANJI_CHECKPOINT_RESUME_ISSUANCE_MUST_BE_SEPARATE", failure.getMessage());
            assertTrue(clients.constructed().isEmpty());
            verifyNoInteractions(fixture.oauth);
        }
    }

    @Test
    void assembles_exact_issuer_and_same_ticket_source_without_issuing() {
        var fixture = new Fixture();
        var ordinary = authority("existing-start");
        var resume = authority("independent-resume");
        var deployment = new TianjiCheckpointResumeApiConfiguration.Deployment(fixture.recovery, "recovery_role", resume);
        var rbac = new Rbac3StarterProperties();
        rbac.setSystemCode("tianji");
        rbac.getAuthorization().setEndpoint("https://decision.example");
        rbac.getAuthorization().getServiceToken().setResourceUri("https://decision.example");
        try (var issuance = mockConstruction(HttpExecutionGrantClient.class, (mock, context) -> {
                 assertEquals(ISSUER, context.arguments().get(1));
                 var service = (IdpServiceTokenRequest) context.arguments().get(2);
                 assertEquals("independent-resume", service.registrationId());
                 assertEquals(Set.of(IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE), service.scopes());
             });
             var decisions = mockConstruction(HttpExecutionAuthorizationClient.class, (mock, context) -> {
                 assertEquals(ISSUER, context.arguments().get(1));
                 var service = (IdpServiceTokenRequest) context.arguments().get(2);
                 assertEquals("independent-resume", service.registrationId());
                 assertEquals(Set.of(ExecutionAuthorizationContract.SERVICE_SCOPE), service.scopes());
             })) {
            var handoff = fixture.handoff(ordinary, resume);
            try (var authority = fixture.configuration.tianjiCheckpointResumeGrantAuthorizer(deployment, handoff,
                    fixture.starter, rbac, fixture.issuers, fixture.oauth, fixture.mapper)) {
                var binding = fixture.configuration.tianjiCheckpointResumeBinding(deployment, authority);
                assertSame(fixture.recovery, binding.recoverySource());
                assertSame(authority, binding.resumeAuthority());
                assertTrue(authority.usesTicketSource(handoff));
                assertTrue(authority.usesResource(RESOURCE));
            }
            assertEquals(1, issuance.constructed().size());
            assertEquals(1, decisions.constructed().size());
            verifyNoInteractions(issuance.constructed().getFirst(), decisions.constructed().getFirst(), fixture.recovery);
        }
    }

    private static IdentityAuthorityProperties authority(String registration) {
        var binding = new IdentityAuthorityProperties.Binding();
        binding.setIssuer(ISSUER);
        binding.setSourceResource(RESOURCE);
        binding.setRegistrationId(registration);
        binding.setAudience(ISSUER_AUDIENCE);
        binding.setTenantId("7");
        binding.setContext(ServiceTokenContext.TENANT);
        binding.setScopes(Set.of(IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE));
        var props = new IdentityAuthorityProperties();
        props.setBaseUri(ISSUER_AUDIENCE);
        props.setBindings(Map.of("tenant-7", binding));
        return props;
    }

    private static final class Fixture {
        final TianjiCheckpointResumeApiConfiguration configuration = new TianjiCheckpointResumeApiConfiguration();
        final IdpStarterProperties starter = new IdpStarterProperties();
        final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
        final IdpServiceOAuth2Client oauth = mock(IdpServiceOAuth2Client.class);
        final DataSource recovery = mock(DataSource.class);
        final ObjectMapper mapper = new ObjectMapper();

        Fixture() {
            starter.setResourceUri(RESOURCE);
            starter.getServiceClient().setAppId("agent-server");
            var trust = mock(TrustedIssuerRegistry.TrustedIssuerVO.class);
            when(trust.realmType()).thenReturn(TrustedIssuerRegistry.RealmType.TENANT);
            when(trust.tenantId()).thenReturn("7");
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        }

        TianjiCheckpointResumeGrantHandoff handoff(IdentityAuthorityProperties ordinary, IdentityAuthorityProperties resume) {
            return configuration.tianjiCheckpointResumeGrantHandoff(
                    new TianjiCheckpointResumeApiConfiguration.Deployment(recovery, "recovery_role", resume),
                    starter, ordinary, issuers, oauth, mapper);
        }
    }
}
