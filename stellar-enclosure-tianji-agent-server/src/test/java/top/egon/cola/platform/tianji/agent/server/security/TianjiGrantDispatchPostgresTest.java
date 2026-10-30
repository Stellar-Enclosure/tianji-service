package top.egon.cola.platform.tianji.agent.server.security;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.*;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Two pollers contend on an isolated PostgreSQL ledger with a real ADK runner and fake model. */
@EnabledIfSystemProperty(named = "tianji.grant.dispatch.postgres.test.url", matches = ".+")
@Timeout(60)
class TianjiGrantDispatchPostgresTest {
    @Test void two_nodes_admit_one_model_invocation_for_the_same_grant_ready_run() throws Exception {
        String url = System.getProperty("tianji.grant.dispatch.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("dispatch tests require an explicit loopback test database");
        }
        var source = new DriverManagerDataSource(url); var jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("dispatch tests require a fresh Agent database");
        }
        var flyway = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("9").cleanDisabled(true).load();
        flyway.migrate(); flyway.validate();
        var ledger = new AgentLedgerService(source); var store = new AgentExecutionGrantStore(source, ledger);
        URI issuer = URI.create("https://idp.example/tenants/88"), resource = URI.create("https://api.example/tianji");
        var actor = TrustedAgentActor.verified(88, TrustedAgentActor.SubjectType.USER, "alice", issuer.toString());
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                5, 0, 1000, 500, new BigDecimal("2.00"), "USD", 2, Set.of(), null, null);
        ledger.publish(actor, new AgentDefinitionSpecDTO("model", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1"), policy,
                Map.of("chatModelBeanName", "fixtureModel", "modelName", "fixture-model", "rootAgentName", "writer",
                        "agents", List.of(Map.of("name", "writer", "instruction", "write")))));
        var run = ledger.start(actor, new StartAgentRunCommand("model", 1, null, "request", null, null,
                new AgentInputDTO("input", List.of(), Map.of()), null));
        var reservation = store.reserve(actor, run.runId(), resource).orElseThrow();
        assertTrue(store.complete(reservation, new AgentExecutionGrant.Ticket("9" + "1".repeat(62),
                "ENC:v1:key:nonce:sealed", ledger.databaseNow().plusSeconds(45))));
        var registry = mock(TrustedIssuerRegistry.class);
        var trust = new TrustedIssuerRegistry.TrustedIssuerVO(issuer, URI.create("https://idp.example/jwks"), "tenant:88",
                TrustedIssuerRegistry.RealmType.TENANT, "88", TrustedIssuerRegistry.Status.ACTIVE, 1);
        when(registry.findByIssuer(issuer.toString())).thenReturn(Optional.of(trust));
        var rbac = mock(HttpExecutionAuthorizationClient.class);
        var bothClaims = new CountDownLatch(2); var release = new CountDownLatch(1); var calls = new AtomicInteger();
        when(rbac.decide(any(), any())).thenAnswer(inv -> {
            if (calls.incrementAndGet() <= 2) { bothClaims.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); }
            ExecutionAuthorizationContract.Request request = inv.getArgument(0);
            HttpExecutionAuthorizationClient.Expectation expected = inv.getArgument(1);
            return new ExecutionAuthorizationContract.Decision(request.grantId(), expected.issuer().toString(), expected.tenantId(),
                    expected.subject(), expected.resource().toString(), expected.purpose(), expected.intentId(), expected.bindingSha256(),
                    expected.sessionRequired(), expected.deadlineAt(), Instant.now().plusSeconds(20), 1, 1);
        });
        var streams = new AtomicInteger(); var model = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(model.getOptions()).thenReturn(org.springframework.ai.model.tool.ToolCallingChatOptions.builder().model("fixture-model").build());
        when(model.stream(any(org.springframework.ai.chat.prompt.Prompt.class))).thenAnswer(inv -> reactor.core.publisher.Flux.defer(() -> {
            streams.incrementAndGet();
            var generation = new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("done"),
                    org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("stop").build());
            return reactor.core.publisher.Flux.just(new org.springframework.ai.chat.model.ChatResponse(List.of(generation),
                    org.springframework.ai.chat.metadata.ChatResponseMetadata.builder().usage(new org.springframework.ai.chat.metadata.DefaultUsage(10, 5, 15)).build()));
        }));
        var validators = jakarta.validation.Validation.buildDefaultValidatorFactory();
        var control = Executors.newScheduledThreadPool(2, task -> Thread.ofPlatform().daemon().name("dispatch-test-control").unstarted(task));
        var hostA = Executors.newSingleThreadExecutor(); var hostB = Executors.newSingleThreadExecutor();
        try (var authorizer = new TianjiExecutionGrantAuthorizer(ledger, store, resource, "tianji", registry,
                Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(issuer, "88", rbac)),
                Duration.ofSeconds(5), 4, Clock.systemUTC())) {
            var flow = new top.egon.cola.component.agentflow.runtime.AgentFlowFactory(new top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory(),
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
            var worker = new top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor(ledger, flow, validator,
                    Map.of("fixtureModel", model), Map.of(), control, estimator, null, false, authorizer);
            var dispatcherA = new AgentRunDispatcher(hostA, worker, ledger); var dispatcherB = new AgentRunDispatcher(hostB, worker, ledger);
            var otherLedger = new AgentLedgerService(source);
            assertTrue(dispatcherA.usesLedger(ledger)); assertFalse(dispatcherA.usesLedger(otherLedger));
            assertTrue(authorizer.usesLedger(ledger)); assertFalse(authorizer.usesLedger(otherLedger));
            try (var wrongStoreAuthorizer = new TianjiExecutionGrantAuthorizer(ledger, new AgentExecutionGrantStore(source, otherLedger),
                    resource, "tianji", registry,
                    Map.of(actor.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(issuer, "88", rbac)),
                    Duration.ofSeconds(5), 4, Clock.systemUTC())) {
                assertFalse(wrongStoreAuthorizer.usesLedger(ledger));
            }
            var schedulerA = mock(TaskScheduler.class); var schedulerB = mock(TaskScheduler.class);
            var callbackA = new AtomicReference<Runnable>(); var callbackB = new AtomicReference<Runnable>();
            ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
            doAnswer(inv -> { callbackA.set(inv.getArgument(0)); return scheduled; }).when(schedulerA).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
            doAnswer(inv -> { callbackB.set(inv.getArgument(0)); return scheduled; }).when(schedulerB).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
            try (var pollerA = new TianjiGrantDispatchPoller(ledger, dispatcherA, authorizer, schedulerA, 10, 2, Duration.ofSeconds(1), Duration.ofSeconds(5));
                 var pollerB = new TianjiGrantDispatchPoller(ledger, dispatcherB, authorizer, schedulerB, 10, 2, Duration.ofSeconds(1), Duration.ofSeconds(5))) {
                pollerA.start(); pollerB.start(); callbackA.get().run(); callbackB.get().run();
                assertTrue(bothClaims.await(10, TimeUnit.SECONDS)); release.countDown();
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                        assertEquals(AgentRunStatusEnum.COMPLETED, ledger.get(actor, new GetAgentRunQuery(run.runId())).status()));
                assertEquals(1, streams.get()); assertEquals(1, ledger.get(actor, new GetAgentRunQuery(run.runId())).attempt());
            }
            var next = ledger.start(actor, new StartAgentRunCommand("model", 1, null, "automatic", null, null,
                    new AgentInputDTO("input", List.of(), Map.of()), null));
            var nextReservation = store.reserve(actor, next.runId(), resource).orElseThrow();
            assertTrue(store.complete(nextReservation, new AgentExecutionGrant.Ticket("9" + "2".repeat(62),
                    "ENC:v1:key:nonce:automatic", ledger.databaseNow().plusSeconds(45))));
            var autoControl = new java.util.concurrent.ScheduledThreadPoolExecutor(2);
            autoControl.setRemoveOnCancelPolicy(true);
            var autoDispatch = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(2));
            var autoScheduler = mock(TaskScheduler.class); var autoCallback = new AtomicReference<Runnable>();
            var reconcileScheduler = mock(TaskScheduler.class); var lifecycle = new java.util.ArrayList<String>();
            doAnswer(inv -> { lifecycle.add("reconcile"); return mock(ScheduledFuture.class); })
                    .when(reconcileScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
            doAnswer(inv -> { lifecycle.add("dispatch"); autoCallback.set(inv.getArgument(0)); return scheduled; })
                    .when(autoScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
            try {
                new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                                top.egon.cola.component.agentflow.autoconfigure.AgentFlowAutoConfiguration.class,
                                top.egon.cola.platform.tianji.agent.autoconfigure.TianjiAgentRuntimeAutoConfiguration.class,
                                top.egon.cola.platform.tianji.agent.autoconfigure.TianjiAgentReconciliationAutoConfiguration.class))
                        .withUserConfiguration(TianjiGrantDispatchConfiguration.class)
                        .withPropertyValues("stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.dispatch.enabled=true",
                                "stellar.tianji.agent.execution-authorization.enabled=true", "stellar.tianji.agent.reconciliation.enabled=true",
                                "egon.cola.component.agent-flow.enabled=true",
                                "egon.cola.component.agent-flow-registry.enabled=false")
                        .withBean(javax.sql.DataSource.class, () -> source)
                        .withBean(AgentLedgerService.class, () -> ledger)
                        .withBean(TianjiExecutionGrantAuthorizer.class, () -> authorizer)
                        .withBean(top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator.class, () -> estimator)
                        .withBean("fixtureModel", org.springframework.ai.chat.model.ChatModel.class, () -> model)
                        .withBean("tianjiAgentControlExecutor", java.util.concurrent.ScheduledThreadPoolExecutor.class, () -> autoControl)
                        .withBean("tianjiAgentDispatchExecutor", java.util.concurrent.ThreadPoolExecutor.class, () -> autoDispatch)
                        .withBean("tianjiAgentDispatchScheduler", TaskScheduler.class, () -> autoScheduler)
                        .withBean("tianjiAgentReconciliationScheduler", TaskScheduler.class, () -> reconcileScheduler)
                        .run(context -> {
                            assertNull(context.getStartupFailure());
                            assertEquals(List.of("reconcile", "dispatch"), lifecycle);
                            assertTrue(context.getBean(AgentRunDispatcher.class).usesAuthorization(authorizer));
                            assertNotNull(autoCallback.get());
                            autoCallback.get().run();
                            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                                    assertEquals(AgentRunStatusEnum.COMPLETED, ledger.get(actor, new GetAgentRunQuery(next.runId())).status()));
                            assertEquals(2, streams.get());
                        });
            } finally { autoDispatch.shutdownNow(); autoControl.shutdownNow(); }
        } finally {
            release.countDown(); hostA.shutdownNow(); hostB.shutdownNow(); control.shutdownNow(); validators.close();
        }
    }
}
