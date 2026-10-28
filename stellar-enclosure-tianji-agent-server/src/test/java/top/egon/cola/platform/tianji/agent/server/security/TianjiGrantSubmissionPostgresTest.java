package top.egon.cola.platform.tianji.agent.server.security;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.RequestContextFilter;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.application.session.AgentSessionManager;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.api.AgentApiErrors;
import top.egon.cola.platform.tianji.agent.server.api.AgentRunController;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CreateAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianquan.shoubing.contract.execution.ExecutionGrantContract;
import top.egon.cola.platform.tianquan.shoubing.starter.execution.HttpExecutionGrantClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named = "tianji.grant.http.postgres.test.url", matches = ".+")
@Timeout(60)
class TianjiGrantSubmissionPostgresTest {
    private static final URI ISSUER = URI.create("https://idp.example/tenants/50001");
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final String TOKEN = "request-user-proof-sentinel";
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(50001, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toString());
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private static AgentLedgerService ledger;
    private static AgentExecutionGrantStore store;
    private final HttpExecutionGrantClient client = mock(HttpExecutionGrantClient.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;

    @BeforeAll static void database() {
        String url = System.getProperty("tianji.grant.http.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("grant HTTP tests require an explicit loopback test database");
        }
        source = new DriverManagerDataSource(url); jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("grant HTTP tests require a fresh Agent database");
        }
        var flyway = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").cleanDisabled(true).load();
        flyway.migrate(); flyway.validate(); ledger = new AgentLedgerService(source); store = new AgentExecutionGrantStore(source, ledger);
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                5, 0, 1000, 500, new BigDecimal("2.00"), "USD", 2, Set.of(), null, null);
        ledger.publish(ACTOR, new AgentDefinitionSpecDTO("review", 1, "SEQUENTIAL", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1"), policy, Map.of()));
        ledger.publish(ACTOR, new AgentDefinitionSpecDTO("model", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1"), policy,
                Map.of("chatModelBeanName", "fixtureModel", "modelName", "fixture-model", "rootAgentName", "writer",
                        "agents", List.of(Map.of("name", "writer", "instruction", "write")))));
        new AgentSessionManager(ledger, source).create(ACTOR, new CreateAgentSessionCommand("session-1"));
    }

    @BeforeEach void setup() {
        var trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:50001",
                TrustedIssuerRegistry.RealmType.TENANT, "50001", TrustedIssuerRegistry.Status.ACTIVE, 1);
        var issuers = mock(TrustedIssuerRegistry.class); when(issuers.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(trust));
        var issuer = new TianjiExecutionGrantIssuer(ledger, store, RESOURCE, issuers, Map.of(ISSUER, client));
        var access = mock(TianjiAgentAccess.class);
        var authorized = new TianjiAgentAccess.Authorized(ACTOR, Instant.now().plusSeconds(300), ISSUER);
        when(access.require(anyString())).thenReturn(authorized);
        @SuppressWarnings("unchecked") ObjectProvider<TianjiExecutionGrantIssuer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(issuer);
        var controller = new AgentRunController(ledger, access, provider,
                new MockEnvironment().withProperty("stellar.tianji.agent.execution-grants.enabled", "true"));
        Filter proof = (request, response, chain) -> {
            var http = (HttpServletRequest) request; VerifiedUserTokenCarrier.set(http, TOKEN);
            try { chain.doFilter(request, response); } finally { VerifiedUserTokenCarrier.clear(http); }
        };
        mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new RequestContextFilter(), proof)
                .setControllerAdvice(new AgentApiErrors()).build();
        when(client.issue(any())).thenAnswer(invocation -> issued(invocation.getArgument(0)));
    }

    @Test void submission_persists_only_sealed_evidence_and_same_key_reuses_it() throws Exception {
        var command = command("grant-success", null);
        String response = mvc.perform(post("/api/tianji/v1/agent-runs").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(command))).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        var run = json.readValue(response, AgentRunDTO.class);
        var authorization = ledger.executionMaterial(ACTOR, new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(run.runId())).authorization();
        var ticket = store.current(authorization, RESOURCE).orElseThrow();
        assertTrue(ticket.ciphertext().startsWith("ENC:v1:")); assertFalse(ticket.ciphertext().contains(TOKEN));
        String repeated = mvc.perform(post("/api/tianji/v1/agent-runs").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(command))).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(run.runId(), json.readValue(repeated, AgentRunDTO.class).runId()); verify(client, times(1)).issue(any());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run_execution_grant WHERE tenant_id=? AND run_id=?",
                Integer.class, ACTOR.tenantId(), run.runId()));
    }

    @Test void failed_issue_leaves_one_queued_run_recoverable_by_the_same_key() throws Exception {
        doThrow(new HttpExecutionGrantClient.ExecutionGrantUnavailableException()).doAnswer(invocation -> issued(invocation.getArgument(0)))
                .when(client).issue(any());
        var command = command("grant-retry", null);
        String failure = mvc.perform(post("/api/tianji/v1/agent-runs").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(command))).andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertFalse(failure.contains(TOKEN));
        String runId = jdbc.queryForObject("SELECT run_id FROM tianji_agent.agent_run WHERE tenant_id=? AND idempotency_key=?",
                String.class, ACTOR.tenantId(), "grant-retry");
        assertEquals(AgentRunStatusEnum.QUEUED, ledger.get(ACTOR, new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(runId)).status());
        String result = mvc.perform(post("/api/tianji/v1/agent-runs").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(command))).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(runId, json.readValue(result, AgentRunDTO.class).runId());
        assertEquals(2L, jdbc.queryForObject("SELECT issue_epoch FROM tianji_agent.agent_run_execution_grant WHERE tenant_id=? AND run_id=?",
                Long.class, ACTOR.tenantId(), runId));
    }

    @Test void session_start_binds_the_additional_session_requirement() throws Exception {
        var command = command("grant-session", "session-1");
        mvc.perform(post("/api/tianji/v1/agent-runs/with-session").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(command))).andExpect(status().isAccepted());
        verify(client).issue(argThat(value -> value.sessionRequired() && value.userToken().equals(TOKEN)
                && value.purpose().equals(AgentRunAuthorization.PURPOSE)));
    }

    @Test void stored_user_intent_can_be_rechecked_without_the_original_bearer() throws Exception {
        var run = submit("grant-authorize");
        var authorization = ledger.executionMaterial(ACTOR, new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(run.runId())).authorization();
        var rbac = mock(top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.class);
        when(rbac.decide(any(), any())).thenAnswer(invocation -> decision(invocation.getArgument(0), invocation.getArgument(1)));
        try (var verifier = authorizer(rbac)) {
            assertEquals(AgentRunAuthorization.Decision.ALLOW, verifier.authorize(authorization, AgentRunAuthorization.Boundary.CLAIM));
            verify(rbac).decide(argThat(value -> value.ciphertext().startsWith("ENC:v1:") && !value.ciphertext().contains(TOKEN)),
                    argThat(value -> value.subject().equals("alice") && value.intentId().equals(run.runId())));
        }
    }

    @Test void cancellation_during_remote_authorization_returns_unavailable_without_holding_the_run_lock() throws Exception {
        var run = submit("grant-auth-cancel");
        var authorization = ledger.executionMaterial(ACTOR, new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(run.runId())).authorization();
        var rbac = mock(top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.class);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(rbac.decide(any(), any())).thenAnswer(invocation -> {
            entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS));
            return decision(invocation.getArgument(0), invocation.getArgument(1));
        });
        try (var verifier = authorizer(rbac); var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(() -> verifier.authorize(authorization, AgentRunAuthorization.Boundary.CLAIM));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            ledger.cancel(ACTOR, new CancelAgentRunCommand(run.runId(), "cancel-authorization", run.revision(), "test"));
            release.countDown();
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, result.get(10, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }

    @Test void actual_adk_executor_rechecks_the_grant_before_model_invocation() throws Exception {
        var deniedRun = submitModel("grant-model-denied");
        var rbac = mock(top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.class);
        when(rbac.decide(any(), any())).thenAnswer(invocation -> decision(invocation.getArgument(0), invocation.getArgument(1)))
                .thenThrow(new top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException());
        var streams = new java.util.concurrent.atomic.AtomicInteger();
        var model = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(model.getOptions()).thenReturn(org.springframework.ai.model.tool.ToolCallingChatOptions.builder().model("fixture-model").build());
        when(model.stream(any(org.springframework.ai.chat.prompt.Prompt.class))).thenAnswer(invocation -> reactor.core.publisher.Flux.defer(() -> {
            streams.incrementAndGet();
            var generation = new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("done"),
                    org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("stop").build());
            return reactor.core.publisher.Flux.just(new org.springframework.ai.chat.model.ChatResponse(List.of(generation),
                    org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().usage(new org.springframework.ai.chat.metadata.DefaultUsage(10, 5, 15)).build()));
        }));
        var validators = jakarta.validation.Validation.buildDefaultValidatorFactory();
        var scheduler = Executors.newScheduledThreadPool(2, task -> Thread.ofPlatform().daemon().name("grant-test-control").unstarted(task));
        try (var verifier = authorizer(rbac)) {
            var flowFactory = new top.egon.cola.component.agentflow.runtime.AgentFlowFactory(
                    new top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory(),
                    new top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory(List.of(
                            new top.egon.cola.component.agentflow.workflow.SequentialAgentWorkflowBuilderStrategy(),
                            new top.egon.cola.component.agentflow.workflow.ParallelAgentWorkflowBuilderStrategy(),
                            new top.egon.cola.component.agentflow.workflow.LoopAgentWorkflowBuilderStrategy())));
            var validator = new top.egon.cola.component.agentflow.config.AgentFlowConfigValidator(
                    new top.egon.cola.component.common.core.validation.ValidationUtils(validators.getValidator()));
            var estimator = new top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator() {
                @Override public Estimate estimate(top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot snapshot, com.google.adk.models.LlmRequest request) {
                    return new Estimate(20, 30, new BigDecimal("0.10"));
                }
                @Override public ContextWindow contextWindow(top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot snapshot, com.google.adk.models.LlmRequest request) {
                    return new ContextWindow(request.model().orElseThrow(), snapshot.definition().dependencies().modelRouteRef(),
                            snapshot.definition().dependencies().policyVersionRef(), 100_000, 10);
                }
                @Override public top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement actualUsage(
                        top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot snapshot, com.google.adk.models.LlmResponse response) {
                    return new top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement(10L, 5L, new BigDecimal("0.05"));
                }
            };
            var executor = new top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor(ledger, flowFactory, validator,
                    Map.of("fixtureModel", model), Map.of(), scheduler, estimator, null, false, verifier);
            var denied = executor.executeQueued(ACTOR, new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(deniedRun.runId()));
            assertEquals(AgentRunStatusEnum.SUSPENDED, denied.status()); assertEquals("AUTHORIZATION_DENIED", denied.waitReason());
            assertEquals(0, streams.get()); verify(rbac, times(2)).decide(any(), any());
            doAnswer(invocation -> decision(invocation.getArgument(0), invocation.getArgument(1))).when(rbac).decide(any(), any());
            var allowedRun = submitModel("grant-model-allowed");
            assertEquals(AgentRunStatusEnum.COMPLETED, executor.executeQueued(ACTOR,
                    new top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery(allowedRun.runId())).status());
            assertEquals(1, streams.get());
        } finally { scheduler.shutdownNow(); validators.close(); }
    }

    @Test void cancellation_during_remote_issue_cannot_publish_a_late_ticket() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return issued(invocation.getArgument(0)); })
                .when(client).issue(any());
        var command = command("grant-cancel-race", null);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> mvc.perform(post("/api/tianji/v1/agent-runs").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(command))).andReturn().getResponse());
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            String runId = jdbc.queryForObject("SELECT run_id FROM tianji_agent.agent_run WHERE tenant_id=? AND idempotency_key=?",
                    String.class, ACTOR.tenantId(), "grant-cancel-race");
            ledger.cancel(ACTOR, new CancelAgentRunCommand(runId, "cancel", 0, "test"));
            release.countDown(); var response = future.get(10, TimeUnit.SECONDS);
            assertEquals(202, response.getStatus()); assertEquals(AgentRunStatusEnum.CANCELLED, json.readValue(response.getContentAsString(), AgentRunDTO.class).status());
            assertNull(jdbc.queryForObject("SELECT ciphertext FROM tianji_agent.agent_run_execution_grant WHERE tenant_id=? AND run_id=?",
                    String.class, ACTOR.tenantId(), runId));
        } finally { release.countDown(); }
    }

    @Test void enabled_configuration_requires_v6_and_matching_source_without_acquiring_a_token() {
        var issuerRegistry = mock(TrustedIssuerRegistry.class);
        var trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:50001",
                TrustedIssuerRegistry.RealmType.TENANT, "50001", TrustedIssuerRegistry.Status.ACTIVE, 1);
        when(issuerRegistry.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(trust));
        var starter = new top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties();
        starter.setResourceUri(RESOURCE); starter.getServiceClient().setAppId("tianji");
        var authority = new top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties();
        authority.setBaseUri(URI.create("https://idp.example"));
        var binding = new top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties.Binding();
        binding.setIssuer(ISSUER); binding.setSourceResource(RESOURCE); binding.setRegistrationId("registration");
        binding.setAudience(URI.create("https://api.example/idp")); binding.setContext(top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext.TENANT);
        binding.setTenantId("50001"); binding.setScopes(Set.of(top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE));
        authority.setBindings(Map.of("tianji", binding));
        var oauth = mock(top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client.class);
        when(oauth.authorizationKey(any())).thenAnswer(inv -> {
            top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest value = inv.getArgument(0);
            return new top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey(ISSUER.toString(), value.registrationId(),
                    "tianji", 1, value.appId(), value.audience(), value.context(), value.tenantId(), value.scopes());
        });
        var runner = new ApplicationContextRunner().withUserConfiguration(TianjiExecutionGrantConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.execution-grants.enabled=true", "stellar.tianji.agent.api.enabled=true")
                .withBean(javax.sql.DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger)
                .withBean("idpStarterProperties", top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties.class, () -> starter)
                .withBean("identityAuthorityProperties", top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties.class, () -> authority)
                .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, () -> issuerRegistry)
                .withBean("idpServiceOAuth2Client", top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client.class, () -> oauth)
                .withBean("jacksonObjectMapper", com.fasterxml.jackson.databind.ObjectMapper.class, com.fasterxml.jackson.databind.ObjectMapper::new);
        runner.run(context -> { assertNull(context.getStartupFailure()); assertNotNull(context.getBean(TianjiExecutionGrantIssuer.class)); });
        verify(oauth, never()).authorize(any());
        var rbac = new top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties();
        rbac.setSystemCode("tianji"); rbac.getAuthorization().setEndpoint("https://rbac.example");
        rbac.getAuthorization().getServiceToken().setResourceUri("https://api.example/rbac");
        runner.withUserConfiguration(TianjiExecutionAuthorizationConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.execution-authorization.enabled=true")
                .withBean(top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties.class, () -> rbac)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization.class));
                });
        verify(oauth, never()).authorize(any());
        var contexts = org.mockito.ArgumentCaptor.forClass(top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest.class);
        verify(oauth, atLeast(2)).authorizationKey(contexts.capture());
        assertTrue(contexts.getAllValues().stream().anyMatch(value -> value.registrationId().equals("registration")
                && value.tenantId().equals("50001") && value.audience().equals(URI.create("https://api.example/rbac"))
                && value.scopes().equals(Set.of(top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract.SERVICE_SCOPE))));
        String missingUrl = System.getProperty("tianji.grant.missing.postgres.test.url");
        assertNotNull(missingUrl);
        assertTrue(missingUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}"));
        var missing = new DriverManagerDataSource(missingUrl); var missingLedger = new AgentLedgerService(missing);
        assertThrows(top.egon.cola.platform.tianji.agent.domain.AgentLedgerException.class,
                () -> new TianjiExecutionGrantConfiguration().tianjiExecutionGrantStore(missing, missingLedger,
                        new MockEnvironment().withProperty("stellar.tianji.agent.api.enabled", "true")));
    }

    private static StartAgentRunCommand command(String key, String session) {
        return new StartAgentRunCommand("review", 1, session, key, null, null, new AgentInputDTO("input", List.of(), Map.of()), null);
    }
    private AgentRunDTO submit(String key) throws Exception {
        String body = mvc.perform(post("/api/tianji/v1/agent-runs").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(command(key, null)))).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(body, AgentRunDTO.class);
    }

    private AgentRunDTO submitModel(String key) throws Exception {
        var command = new StartAgentRunCommand("model", 1, null, key, null, null, new AgentInputDTO("input", List.of(), Map.of()), null);
        String body = mvc.perform(post("/api/tianji/v1/agent-runs").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(command))).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return json.readValue(body, AgentRunDTO.class);
    }

    private static TianjiExecutionGrantAuthorizer authorizer(
            top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient client) {
        var registry = mock(TrustedIssuerRegistry.class);
        var trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:50001",
                TrustedIssuerRegistry.RealmType.TENANT, "50001", TrustedIssuerRegistry.Status.ACTIVE, 1);
        when(registry.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(trust));
        return new TianjiExecutionGrantAuthorizer(ledger, store, RESOURCE, "tianji", registry,
                Map.of(ACTOR.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER, "50001", client)),
                Duration.ofSeconds(5), 2, java.time.Clock.systemUTC());
    }

    private static top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract.Decision decision(
            top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract.Request request,
            top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.Expectation expected) {
        return new top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract.Decision(request.grantId(),
                expected.issuer().toString(), expected.tenantId(), expected.subject(), expected.resource().toString(), expected.purpose(),
                expected.intentId(), expected.bindingSha256(), expected.sessionRequired(), expected.deadlineAt(), Instant.now().plusSeconds(20), 1, 1);
    }
    private static ExecutionGrantContract.Issued issued(ExecutionGrantContract.IssueRequest request) {
        String decimal = new BigInteger(request.intentId().replace("-", ""), 16).toString();
        String id = "9" + "0".repeat(62 - decimal.length()) + decimal;
        return new ExecutionGrantContract.Issued(new ExecutionGrantContract.Ticket(id, "ENC:v1:key:nonce:sealed"), request.deadlineAt().minusSeconds(5));
    }
}
