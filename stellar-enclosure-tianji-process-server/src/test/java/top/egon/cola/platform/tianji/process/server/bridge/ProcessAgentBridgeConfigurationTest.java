package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.AsyncJobExecutorConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.context.LifecycleProcessor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import top.egon.cola.platform.tianji.client.HttpLinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpClientCredentialsRequestEntityConverter;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessAgentBridgeConfigurationTest {
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final URI RESOURCE = URI.create("https://api.example/agent");
    private final ProcessAgentBridgeConfiguration configuration = new ProcessAgentBridgeConfiguration();
    private final AgentResultInboxProperties inbox = new AgentResultInboxProperties(List.of(new AgentResultInboxProperties.Producer(
            20, ISSUER.toString(), "agent", "agent-east", "process-east", ISSUER.toString(), "process-worker")), null);
    private final AgentResultProducerRegistry producers = new AgentResultProducerRegistry(inbox.registrations());
    private final IdpStarterProperties starter = starter();
    private final TrustedIssuerRegistry issuers = new TrustedIssuerRegistry(starter);

    @Test
    void bridge_is_absent_by_default_and_requires_all_three_security_and_reception_flags() {
        new ApplicationContextRunner().withUserConfiguration(ProcessAgentBridgeConfiguration.class)
                .run(context -> { assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(ProcessAgentBridgePoller.class).isEmpty()); });
        new ApplicationContextRunner().withUserConfiguration(ProcessAgentBridgeConfiguration.class)
                .withPropertyValues("stellar.tianji.process.agent-bridge.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
        assertThrows(IllegalArgumentException.class, () -> properties(List.of(binding()), 33));
        assertThrows(IllegalArgumentException.class, () -> properties(List.of(binding(), binding()), 4));
    }

    @Test
    void real_oauth_registration_identity_is_checked_without_authorizing_or_contacting_an_issuer() {
        var manager = mock(OAuth2AuthorizedClientManager.class);
        var store = mock(OAuth2AuthorizedClientService.class);
        try (var oauth = oauth(manager, store, "process-worker")) {
            assertDoesNotThrow(() -> validate(properties(), topics(Duration.ofSeconds(15)), oauth));
        }
        try (var oauth = oauth(manager, store, "another-service")) {
            assertThrows(IllegalStateException.class, () -> validate(properties(), topics(Duration.ofSeconds(15)), oauth));
        }
        verifyNoInteractions(manager, store);
    }

    @Test
    void lease_budget_fixed_submission_window_and_coverage_fail_before_token_authorization() {
        var oauth = mock(IdpServiceOAuth2Client.class);
        assertThrows(IllegalStateException.class, () -> validate(properties(), topics(Duration.ofMillis(10_999)), oauth));
        assertThrows(IllegalStateException.class, () -> validate(properties(), topics(Duration.ofMinutes(2)), oauth));
        var otherInbox = new AgentResultInboxProperties(List.of(new AgentResultInboxProperties.Producer(20, ISSUER.toString(), "agent",
                "agent-east", "other-source", ISSUER.toString(), "process-worker")), null);
        assertThrows(IllegalStateException.class, () -> ProcessAgentBridgeConfiguration.validateBindings(properties(), topics(Duration.ofSeconds(15)),
                new AgentResultProducerRegistry(otherInbox.registrations()), otherInbox, starter, issuers, oauth));
        verifyNoInteractions(oauth);
        when(oauth.authorizationKey(any())).thenAnswer(call -> ServiceAuthorizationKey.from(call.getArgument(0), ISSUER.toString(), "process-worker", 1));
        var uncovered = new WorkerTopicRegistry(List.of(registration(Duration.ofSeconds(15), "agent-start"), registration(Duration.ofSeconds(15), "unbound-topic")));
        assertThrows(IllegalStateException.class, () -> validate(properties(), uncovered, oauth));
        validate(properties(), topics(Duration.ofSeconds(11)), oauth);
        verify(oauth, never()).authorize(any());
        verify(oauth, never()).authorizeBounded(any(), anyString(), any());
    }

    @Test
    void owned_clients_use_only_the_fixed_tenant_resource_and_operation_scope_and_close_with_resources() {
        var engine = initializedEngine();
        var process = (SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
        var manager = (DataSourceTransactionManager) process.getTransactionManager();
        var source = manager.getDataSource();
        var workers = mock(FlowableWorkerService.class);
        var dispatches = mock(AgentDispatchStore.class);
        var oauth = mock(IdpServiceOAuth2Client.class);
        when(oauth.authorizationKey(any())).thenAnswer(call -> ServiceAuthorizationKey.from(call.getArgument(0), ISSUER.toString(), "process-worker", 1));
        var now = Instant.now();
        when(oauth.authorizeBounded(any(), eq(ISSUER.toString()), eq(Duration.ofSeconds(3)))).thenReturn(
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "test-service-token", now, now.plusSeconds(60)));
        var provider = new AtomicReference<Function<HttpLinkedAgentClient.Operation, String>>();
        var topics = topics(Duration.ofSeconds(15));
        try (var clients = mockConstruction(HttpLinkedAgentClient.class, (client, context) -> provider.set((Function<HttpLinkedAgentClient.Operation, String>) context.arguments().get(2)))) {
            try (var resources = configuration.tianjiProcessAgentBridgeResources(engine, workers, dispatches, topics,
                    producers, inbox, properties(), starter, issuers, oauth)) {
                verify(oauth, never()).authorizeBounded(any(), anyString(), any());
                for (var operation : HttpLinkedAgentClient.Operation.values()) assertEquals("test-service-token", provider.get().apply(operation));
                for (String scope : Set.of(LinkedAgentClient.SUBMIT_SCOPE, LinkedAgentClient.RESOLVE_SCOPE, LinkedAgentClient.CLOSE_SCOPE)) {
                    verify(oauth).authorizeBounded(new IdpServiceTokenRequest("agent-client", "process-app", RESOURCE, ServiceTokenContext.TENANT, "20", Set.of(scope)),
                            ISSUER.toString(), Duration.ofSeconds(3));
                }
                verify(workers).requireStorage(process, manager, topics);
                verify(dispatches).requireStorage(source, manager, "tianji_process");
                verifyNoInteractions(source);
            }
            verify(clients.constructed().getFirst()).close();
        }
    }

    @Test
    void spring_assembles_and_disposes_the_bridge_before_native_execution_is_active() {
        var engine = initializedEngine();
        var process = (SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
        var nativeExecutor = process.getAsyncExecutor();
        when(nativeExecutor.isActive()).thenReturn(false);
        var workers = mock(FlowableWorkerService.class);
        var dispatches = mock(AgentDispatchStore.class);
        var reception = mock(AgentResultInbox.class);
        var oauth = mock(IdpServiceOAuth2Client.class);
        when(oauth.authorizationKey(any())).thenAnswer(call -> ServiceAuthorizationKey.from(call.getArgument(0), ISSUER.toString(), "process-worker", 1));
        try (var clients = mockConstruction(HttpLinkedAgentClient.class)) {
            bridgeContext(engine, workers, dispatches, reception, oauth)
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertFalse(context.getBean(ProcessAgentBridgePoller.class).isRunning());
                        assertNotNull(context.getBean(AgentResultCorrelationService.class));
                        assertFalse(context.getBean(ProcessAgentBridgeProperties.class).dispatchEnabled());
                        verify(reception).requireBindings(dispatches, producers);
                        verify(oauth, never()).authorizeBounded(any(), anyString(), any());
                        verify(nativeExecutor, never()).isActive();
                        verifyNoInteractions(((DataSourceTransactionManager) process.getTransactionManager()).getDataSource());
                    });
            assertEquals(1, clients.constructed().size());
            verify(clients.constructed().getFirst()).close();
        }
    }

    @Test
    void bridge_requires_the_native_executor_at_start_even_when_dispatch_is_disabled() {
        var engine = initializedEngine();
        var process = (SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
        var nativeExecutor = process.getAsyncExecutor();
        var oauth = mock(IdpServiceOAuth2Client.class);
        when(oauth.authorizationKey(any())).thenAnswer(call -> ServiceAuthorizationKey.from(call.getArgument(0), ISSUER.toString(), "process-worker", 1));
        try (var clients = mockConstruction(HttpLinkedAgentClient.class)) {
            bridgeContext(engine, mock(FlowableWorkerService.class), mock(AgentDispatchStore.class), mock(AgentResultInbox.class), oauth)
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        var poller = context.getBean(ProcessAgentBridgePoller.class);
                        assertTrue(poller.getPhase() > new SpringProcessEngineConfiguration().getPhase());
                        when(nativeExecutor.isActive()).thenReturn(false);

                        var failure = assertThrows(IllegalStateException.class, poller::start);
                        assertEquals("Worker acquisition requires the native job executor and expired-lock reset", failure.getMessage());
                        assertFalse(poller.isRunning());
                        assertFalse(poller.isDispatching());

                        when(nativeExecutor.isActive()).thenReturn(true);
                        poller.start();
                        assertTrue(poller.isRunning());
                        assertFalse(poller.isDispatching());
                    });
            assertEquals(1, clients.constructed().size());
        }
    }

    @Test
    void bridge_resources_reject_disabled_native_acquisition_and_expired_lock_reset() {
        var engine = initializedEngine();
        var process = (SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
        var nativeJobs = process.getAsyncExecutorConfiguration();
        var workers = mock(FlowableWorkerService.class);
        var dispatches = mock(AgentDispatchStore.class);
        var oauth = mock(IdpServiceOAuth2Client.class);
        Runnable assembly = () -> configuration.tianjiProcessAgentBridgeResources(engine, workers, dispatches, topics(Duration.ofSeconds(15)),
                producers, inbox, properties(), starter, issuers, oauth);

        when(process.isAsyncExecutorActivate()).thenReturn(false);
        assertEquals("Worker acquisition requires the native job executor and expired-lock reset",
                assertThrows(IllegalStateException.class, assembly::run).getMessage());
        when(process.isAsyncExecutorActivate()).thenReturn(true);
        when(nativeJobs.isAsyncJobAcquisitionEnabled()).thenReturn(false);
        assertEquals("Worker acquisition requires the native job executor and expired-lock reset",
                assertThrows(IllegalStateException.class, assembly::run).getMessage());
        when(nativeJobs.isAsyncJobAcquisitionEnabled()).thenReturn(true);
        when(nativeJobs.isResetExpiredJobEnabled()).thenReturn(false);
        assertEquals("Worker acquisition requires the native job executor and expired-lock reset",
                assertThrows(IllegalStateException.class, assembly::run).getMessage());
        verifyNoInteractions(workers, dispatches, oauth);
    }

    private ApplicationContextRunner bridgeContext(ProcessEngine engine, FlowableWorkerService workers, AgentDispatchStore dispatches,
            AgentResultInbox reception, IdpServiceOAuth2Client oauth) {
        return new ApplicationContextRunner().withUserConfiguration(ProcessAgentBridgeConfiguration.class)
                .withBean("lifecycleProcessor", LifecycleProcessor.class, () -> mock(LifecycleProcessor.class))
                .withBean(ProcessEngine.class, () -> engine).withBean(FlowableWorkerService.class, () -> workers)
                .withBean(AgentDispatchStore.class, () -> dispatches).withBean(AgentResultInbox.class, () -> reception)
                .withBean(WorkerTopicRegistry.class, () -> topics(Duration.ofSeconds(15)))
                .withBean(AgentResultProducerRegistry.class, () -> producers).withBean(AgentResultInboxProperties.class, () -> inbox)
                .withBean(IdpStarterProperties.class, () -> starter).withBean(TrustedIssuerRegistry.class, () -> issuers)
                .withBean(IdpServiceOAuth2Client.class, () -> oauth)
                .withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.worker.enabled=true",
                        "stellar.tianji.process.agent-results.enabled=true", "stellar.tianji.process.agent-bridge.enabled=true",
                        "stellar.tianji.process.agent-bridge.dispatch-enabled=false",
                        "stellar.tianji.process.agent-bridge.bindings[0].tenant-id=20",
                        "stellar.tianji.process.agent-bridge.bindings[0].issuer=" + ISSUER,
                        "stellar.tianji.process.agent-bridge.bindings[0].subject-id=process-worker",
                        "stellar.tianji.process.agent-bridge.bindings[0].source-id=process-east",
                        "stellar.tianji.process.agent-bridge.bindings[0].topic=agent-start",
                        "stellar.tianji.process.agent-bridge.bindings[0].schema-version=1",
                        "stellar.tianji.process.agent-bridge.bindings[0].definition-key=assistant",
                        "stellar.tianji.process.agent-bridge.bindings[0].definition-version=1",
                        "stellar.tianji.process.agent-bridge.bindings[0].definition-content-sha256=" + "a".repeat(64),
                        "stellar.tianji.process.agent-bridge.bindings[0].instruction=Review the provided inputs",
                        "stellar.tianji.process.agent-bridge.bindings[0].base-uri=https://agent.example/",
                        "stellar.tianji.process.agent-bridge.bindings[0].resource-uri=" + RESOURCE,
                        "stellar.tianji.process.agent-bridge.bindings[0].registration-id=agent-client");
    }

    private static ProcessEngine initializedEngine() {
        var engine = mock(ProcessEngine.class);
        var process = mock(SpringProcessEngineConfiguration.class);
        var nativeJobs = mock(AsyncJobExecutorConfiguration.class);
        var nativeExecutor = mock(AsyncExecutor.class);
        when(engine.getProcessEngineConfiguration()).thenReturn(process);
        when(process.getTransactionManager()).thenReturn(new DataSourceTransactionManager(mock(DataSource.class)));
        when(process.getDatabaseSchema()).thenReturn("tianji_process");
        when(process.isAsyncExecutorActivate()).thenReturn(true);
        when(process.getAsyncExecutorConfiguration()).thenReturn(nativeJobs);
        when(nativeJobs.isAsyncJobAcquisitionEnabled()).thenReturn(true);
        when(nativeJobs.isResetExpiredJobEnabled()).thenReturn(true);
        when(process.getAsyncExecutor()).thenReturn(nativeExecutor);
        when(nativeExecutor.isActive()).thenReturn(true);
        when(process.getHistoryService()).thenReturn(mock(HistoryService.class));
        when(process.getRuntimeService()).thenReturn(mock(RuntimeService.class));
        when(process.isDbHistoryUsed()).thenReturn(true);
        when(process.getHistoryLevel()).thenReturn(HistoryLevel.ACTIVITY);
        return engine;
    }

    private void validate(ProcessAgentBridgeProperties properties, WorkerTopicRegistry topics, IdpServiceOAuth2Client oauth) {
        ProcessAgentBridgeConfiguration.validateBindings(properties, topics, producers, inbox, starter, issuers, oauth);
    }
    private IdpServiceOAuth2Client oauth(OAuth2AuthorizedClientManager manager, OAuth2AuthorizedClientService store, String subject) {
        var client = ClientRegistration.withRegistrationId("agent-client").clientId(subject).clientSecret("fixture-only")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .issuerUri(ISSUER.toString()).tokenUri(ISSUER + "/oauth2/token").build();
        return IdpServiceOAuth2Client.createConfigured(manager, new IdpClientCredentialsRequestEntityConverter(), Clock.systemUTC(), Duration.ofSeconds(30),
                id -> "agent-client".equals(id) ? client : null, store, issuers, starter);
    }
    private static IdpStarterProperties starter() {
        var value = new IdpStarterProperties();
        value.getServiceClient().setAppId("process-app");
        value.getServiceClient().setRegistrationId("agent-client");
        value.getServiceClient().getCredentialVersions().put("agent-client", 1L);
        var issuer = new IdpStarterProperties.TrustedIssuerSettings();
        issuer.setIssuer(ISSUER.toString()); issuer.setJwksUri(ISSUER + "/jwks"); issuer.setRealmKey("tenant:20");
        issuer.setRealmType("TENANT"); issuer.setTenantId("20"); issuer.setStatus("ACTIVE"); issuer.setTrustVersion(1);
        value.setTrustedIssuers(Map.of("tenant-20", issuer));
        return value;
    }
    private static ProcessAgentBridgeProperties properties() { return properties(List.of(binding()), 4); }
    private static ProcessAgentBridgeProperties properties(List<ProcessAgentBridgeProperties.Binding> bindings, int concurrency) {
        return new ProcessAgentBridgeProperties(bindings, null, concurrency, 0, 0, null, null, null, null, null, 0);
    }
    private static ProcessAgentBridgeProperties.Binding binding() {
        return new ProcessAgentBridgeProperties.Binding(20, ISSUER, "process-worker", "process-east", "agent-start", "1", "assistant", 1,
                "a".repeat(64), "Review the provided inputs", null, URI.create("https://agent.example/"), RESOURCE, "agent-client", null, 0);
    }
    private static WorkerTopicRegistry topics(Duration lease) { return new WorkerTopicRegistry(List.of(registration(lease, "agent-start"))); }
    private static WorkerTopicRegistry.Registration registration(Duration lease, String topic) {
        return new WorkerTopicRegistry.Registration(20, ISSUER.toString(), "process-worker", topic, "1", Map.of(), Map.of("runId", ValueType.STRING),
                Map.of(), Map.of("runId", Set.of("agentRunId")), 4, lease, Duration.ofSeconds(1), WorkerEffect.EXTERNAL_IDEMPOTENT, true);
    }
}
