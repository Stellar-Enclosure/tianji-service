package top.egon.cola.platform.tianji.agent.recovery;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolStartReceiptReconciliationService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolStartReceiptReconciliationService.ReceiptVO;
import top.egon.cola.platform.tianji.agent.recovery.TianjiHistoricalStartReceiptConfiguration.HistoricalStartReceiptBinding;
import top.egon.cola.platform.tianji.agent.recovery.TianjiHistoricalStartReceiptConfiguration.HistoricalStartReceiptHost;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentServerApplication;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedBpmnStartReceiptReader;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedBpmnStartToolBindings;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TianjiHistoricalStartReceiptConfigurationTest {
    private static final String BINDING = "tianjiHistoricalStartReceiptBinding";
    private static final String INVALID = "TIANJI_HISTORICAL_START_RECEIPT_HOST_PREREQUISITES_INVALID";
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final URI RESOURCE = URI.create("https://api.example/process/20");
    private static final String READ_ALIAS = "process-receipt-reader";
    private static final String EXECUTOR = "agent-executor";
    private static final String APP = "tianji-agent";
    private static final TrustedAgentActor OWNER =
            TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://owner.example/20");
    private static final IdpServiceTokenRequest READ_REQUEST =
            new IdpServiceTokenRequest(READ_ALIAS, APP, RESOURCE, ServiceTokenContext.TENANT, "20",
                    Set.of(ProcessStartReceiptClient.READ_SCOPE));

    @Test
    void the_default_application_does_not_import_the_historical_host_even_with_a_binding() {
        var fixture = new Fixture();
        new ApplicationContextRunner().withUserConfiguration(TianjiAgentServerApplication.class)
                .withPropertyValues("spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
                        "egon.cola.component.id.enabled=false")
                .withBean(BINDING, HistoricalStartReceiptBinding.class, fixture::binding)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(HistoricalStartReceiptHost.class)
                        .doesNotHaveBean("webServerFactory"));
        verifyNoInteractions(fixture.source, fixture.readOAuth, fixture.authority);
    }

    @Test
    void explicit_import_requires_the_exact_named_binding() {
        var fixture = new Fixture();
        fixture.runner().withBean("unrelatedHistoricalBinding", HistoricalStartReceiptBinding.class, fixture::binding)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining(BINDING);
                });
        verifyNoInteractions(fixture.source, fixture.readOAuth, fixture.authority);
    }

    @Test
    void historical_host_uses_the_retained_read_binding_and_owns_only_its_resources() {
        var fixture = new Fixture();
        var host = new AtomicReference<HistoricalStartReceiptHost>();
        var intentId = UUID.fromString("62965f56-dbb1-4d84-91e0-a7c5d5f5b9b1");
        var receipt = new ReceiptVO(intentId, "run-one", "process-1", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        try (var http = fixture.mockHttpBuilder();
             var services = mockConstruction(ToolStartReceiptReconciliationService.class, (service, context) -> {
                 var args = context.arguments();
                 assertThat(args.get(0)).isSameAs(fixture.source);
                 assertThat(args.get(1)).isSameAs(fixture.ledger);
                 assertThat(args.get(2)).isSameAs(fixture.authority);
                 assertThat(args.getLast()).isInstanceOf(TianjiFixedBpmnStartReceiptReader.class);
                 when(service.observe(OWNER, "run-one", intentId)).thenReturn(receipt);
             })) {
            fixture.bound().run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(HistoricalStartReceiptHost.class)
                        .doesNotHaveBean(TianjiFixedBpmnStartReceiptReader.class)
                        .doesNotHaveBean(ToolStartReceiptReconciliationService.class)
                        .doesNotHaveBean(HttpClient.class);
                host.set(context.getBean(HistoricalStartReceiptHost.class));
                assertThat(host.get().observe(OWNER, "run-one", intentId)).isSameAs(receipt);
            });
            assertThat(services.constructed()).hasSize(1);
            verify(services.constructed().getFirst()).observe(OWNER, "run-one", intentId);
            verify(fixture.readOAuth).authorizationKey(READ_REQUEST);
            verifyNoMoreInteractions(fixture.readOAuth);
            verify(fixture.httpBuilder).followRedirects(HttpClient.Redirect.NEVER);
            verify(fixture.http).shutdownNow();
            verifyNoInteractions(fixture.source, fixture.authority, fixture.authentication, fixture.converter);

            var closed = assertThrows(AgentLedgerException.class, () -> host.get().observe(OWNER, "run-one", intentId));
            assertThat(closed.code()).isEqualTo(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
            verifyNoMoreInteractions(services.constructed().getFirst());
        }
    }

    @Test
    void wrong_ledger_source_or_disabled_storage_fails_before_credential_and_database_work() {
        var wrongSource = new Fixture();
        when(wrongSource.ledger.usesDataSource(wrongSource.source)).thenReturn(false);
        wrongSource.bound().run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(INVALID));
        verifyNoInteractions(wrongSource.source, wrongSource.readOAuth, wrongSource.authority);

        var disabled = new Fixture();
        disabled.bound().withPropertyValues("stellar.tianji.agent.storage.enabled=false")
                .run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(INVALID));
        verifyNoInteractions(disabled.source, disabled.readOAuth, disabled.authority);
    }

    @Test
    void wrong_registered_client_fails_preflight_before_store_construction_or_token_acquisition() {
        var fixture = new Fixture();
        when(fixture.readOAuth.authorizationKey(READ_REQUEST))
                .thenReturn(ServiceAuthorizationKey.from(READ_REQUEST, ISSUER.toASCIIString(), "foreign-client", 1));
        try (var http = fixture.mockHttpBuilder(); var services = mockConstruction(ToolStartReceiptReconciliationService.class)) {
            fixture.bound().run(context -> assertThat(context.getStartupFailure()).hasRootCauseMessage(INVALID));
            assertThat(services.constructed()).isEmpty();
            verify(fixture.readOAuth).authorizationKey(READ_REQUEST);
            verifyNoMoreInteractions(fixture.readOAuth);
            verify(fixture.http).shutdownNow();
            verifyNoInteractions(fixture.source, fixture.authority, fixture.authentication, fixture.converter);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(TianjiHistoricalStartReceiptConfiguration.class)
    static class ExplicitImporter { }

    private static final class Fixture {
        private final DataSource source = mock(DataSource.class);
        private final AgentLedgerService ledger = mock(AgentLedgerService.class);
        private final IdpServiceOAuth2Client readOAuth = mock(IdpServiceOAuth2Client.class);
        private final HistoricalToolReceiptAuthorization authority = mock(HistoricalToolReceiptAuthorization.class);
        private final TenantIssuerAuthenticationManagerComponent authentication = mock(TenantIssuerAuthenticationManagerComponent.class);
        private final VerifiedPrincipalConverter converter = mock(VerifiedPrincipalConverter.class);
        private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
        private final HttpClient http = mock(HttpClient.class);
        private final HttpClient.Builder httpBuilder = mock(HttpClient.Builder.class);
        private final TianjiFixedBpmnStartToolBindings.BackendVO backend =
                new TianjiFixedBpmnStartToolBindings.BackendVO(20, ISSUER, URI.create("https://process.example"), RESOURCE,
                        "live-start", EXECUTOR, APP, "business", "agent", "test", "release-1", "a".repeat(64), "case_flow",
                        "b".repeat(64), Map.of("amount", ProcessValueTypeEnum.INTEGER), Duration.ofSeconds(5));

        private Fixture() {
            when(ledger.usesDataSource(source)).thenReturn(true);
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(
                    new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/20/jwks"), "tenant-20",
                            TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
            when(readOAuth.authorizationKey(READ_REQUEST))
                    .thenReturn(ServiceAuthorizationKey.from(READ_REQUEST, ISSUER.toASCIIString(), EXECUTOR, 1));
        }

        private HistoricalStartReceiptBinding binding() {
            var descriptor = new ToolDescriptor("process.case.start", 1, "start_case", "Fixed BPMN start",
                    new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                    new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                    new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start", ProcessClient.START_RESOURCE_KEY,
                            backend.bindingSha256(), null));
            var route = new TianjiFixedBpmnStartToolBindings.BindingVO(20, OWNER.issuerFingerprint(), OWNER.subjectType(),
                    "assistant", 1, "c".repeat(64), descriptor.reference(), backend);
            return new HistoricalStartReceiptBinding(new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(route)),
                    Map.of(backend.bindingSha256(), READ_ALIAS), authority, readOAuth);
        }

        private ApplicationContextRunner runner() {
            return new ApplicationContextRunner().withUserConfiguration(ExplicitImporter.class)
                    .withPropertyValues("stellar.tianji.agent.storage.enabled=true")
                    .withBean(DataSource.class, () -> source)
                    .withBean(AgentLedgerService.class, () -> ledger)
                    .withBean("tenantIssuerAuthenticationManagerComponent", TenantIssuerAuthenticationManagerComponent.class, () -> authentication)
                    .withBean("verifiedPrincipalConverter", VerifiedPrincipalConverter.class, () -> converter)
                    .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, () -> issuers);
        }

        private ApplicationContextRunner bound() {
            return runner().withBean(BINDING, HistoricalStartReceiptBinding.class, this::binding);
        }

        private MockedStatic<HttpClient> mockHttpBuilder() {
            when(httpBuilder.followRedirects(any())).thenReturn(httpBuilder);
            when(httpBuilder.connectTimeout(any())).thenReturn(httpBuilder);
            when(httpBuilder.build()).thenReturn(http);
            when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
            var factory = mockStatic(HttpClient.class);
            factory.when(HttpClient::newBuilder).thenReturn(httpBuilder);
            return factory;
        }
    }
}
