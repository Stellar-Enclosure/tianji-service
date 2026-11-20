package top.egon.cola.platform.tianji.process.server.acceptance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionCommand;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionHandle;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionResult;
import top.egon.cola.component.agentflow.api.execution.AgentExecutor;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.OutboxJdbcAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.OutboxStateMachineAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxAutoConfiguration;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.outbox.store.OutboxStore;
import top.egon.cola.component.outbox.transaction.OutboxAfterCommitBuffer;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextConverter;
import top.egon.cola.platform.tianji.agent.application.run.AgentRuntimeExecutionService;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.FrozenSiBundleVO;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.SiResourceVO;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeBindingStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeCallReceiptStore;
import top.egon.cola.platform.tianji.agent.server.api.AgentApiErrors;
import top.egon.cola.platform.tianji.agent.server.api.AgentRequestBodyAdvice;
import top.egon.cola.platform.tianji.agent.server.api.LinkedAgentDispatchController;
import top.egon.cola.platform.tianji.agent.server.api.LinkedAgentRuntimeController;
import top.egon.cola.platform.tianji.agent.server.integration.process.AgentResultDeliveryHandler;
import top.egon.cola.platform.tianji.agent.server.integration.process.OutboxAgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedServiceAdmission;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.VerifiedAgentIssuerFilter;
import top.egon.cola.platform.tianji.client.HttpAgentResultClient;
import top.egon.cola.platform.tianji.client.HttpLinkedAgentClient;
import top.egon.cola.platform.tianji.client.AgentClientException;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand.AgentTaskOverrideVO;
import top.egon.cola.platform.tianji.process.server.api.AgentResultInboxController;
import top.egon.cola.platform.tianji.process.server.bridge.AgentStartWorker;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static top.egon.cola.platform.tianji.process.server.acceptance.ProcessAgentFusionIT.*;

class AgentTaskSnapshotIT {
    private static final String SNAPSHOT_URL = "tianji.process.agent-snapshot.postgres.test.url";
    static final String AS_URL = "tianji.agent.fusion.postgres.test.url";
    static final String AS_SNAPSHOT_URL = "tianji.agent.fusion.snapshot.postgres.test.url";
    private static final String AS_SCHEMA = "tianji_agent";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(8);
    private static final String DESTINATION = "process-fusion-inbox";
    private static final String PROMPT_TEXT = "Fixed Process fusion instruction";
    private static final String PROMPT_SPEC = canonicalSpec(Map.of("promptText", PROMPT_TEXT));

    @Test
    void start_seals_the_exact_template_and_allowed_inputs_before_first_node_and_replay_keeps_them() throws Exception {
        var database = ProcessDatabase.install(SNAPSHOT_URL);
        try (var process = database.open(); var agent = new AgentBoundary(process, AS_SNAPSHOT_URL, SNAPSHOT_URL)) {
            var version1 = agent.publish(1);
            var version2 = agent.publish(2);
            var override = Map.of("assess", new AgentTaskOverrideVO(ref(version1), Map.of("lane", "priority")));
            process.publish(List.of(version1, version2), version2);
            var started = process.startBeforeBootstrap("fixed-config", override);
            String root = started.getInstanceId();
            assertEquals(0, process.count("act_ru_task", "PROC_INST_ID_=?", root));
            assertEquals("1", process.manifestText(root, "templateRef", "version"));
            assertEquals(version1.contentSha256(), process.manifestText(root, "templateRef", "contentSha256"));
            assertEquals("priority", process.manifestText(root, "parameters", "lane"));
            assertEquals(SOURCE, process.manifestText(root, "sourceId"));
            assertEquals(BRIDGE_ACTOR.issuerFingerprint(), process.manifestText(root, "issuer"));
            assertNotNull(process.manifestText(root, "limits"));
            process.runAsync(root);
            assertEquals(1, process.count("act_ru_task", "PROC_INST_ID_=?", root));
            assertThrows(IllegalArgumentException.class, () -> process.start("undeclared",
                    Map.of("assess", new AgentTaskOverrideVO(ref(version1), Map.of("rogue", "deny")))));
            agent.publish(3);
            process.close();
            try (var restarted = database.open()) {
                agent.connectInbox(restarted);
                restarted.authorityTemplates.put(ref(version2), version2);
                assertEquals(root, restarted.start("fixed-config", override).getInstanceId());
                assertEquals(version1.contentSha256(), restarted.manifestText(root, "templateRef", "contentSha256"));
                assertThrows(ProcessConflictException.class, () -> restarted.start("fixed-config",
                        Map.of("assess", new AgentTaskOverrideVO(ref(version2), Map.of("lane", "changed")))));
                assertEquals(1, restarted.count("tianji_process_instance_binding", "tenant_id=?", STARTER.tenantId()));
                restarted.completeHuman(root, Map.of("amount", 17L));
                assertEquals(AgentStartWorker.Outcome.ACKNOWLEDGED, restarted.bridge(agent.client()).runOnce());
                var actual = agent.linkedCommand();
                assertEquals(version1.getTemplateVersion(), actual.getTemplateVersion());
                assertEquals(version1.contentSha256(), actual.getTemplateSha256());
                assertEquals(started.getConfigurationSha256(), actual.getConfigurationSha256());
                assertEquals(Map.of("lane", "priority"), actual.getParameters());
                assertInput(actual.getInput());
                assertEquals(restarted.attemptDeadline(root, "assess"), actual.getAttemptDeadlineAt());
                assertEquals(Duration.ofSeconds(45), restarted.dispatchDuration(root));
                assertEquals(1, agent.runCount());
                agent.revoke(version2);
                restarted.runAsync(root);
                var revoked = restarted.start("revoked-template", Map.of()).getInstanceId();
                restarted.completeHuman(revoked, Map.of("amount", 17L));
                var denied = assertThrows(AgentClientException.class, () -> restarted.bridge(agent.client()).runOnce());
                assertEquals(403, denied.httpStatus());
                assertEquals("TIANJI_PERMISSION_DENIED", denied.code());
                assertEquals("PENDING", restarted.dispatchState(revoked));
                assertEquals(1, agent.runCount());
            }
        }
    }

    @Test
    void frozenExecutionFixtureMatchesThePublishedPrompt() {
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, BRIDGE_ACTOR.subjectId(), ISSUER);
        var selected = withFixedInstruction(template(1));
        var bundle = executionFixture(actor, selected);
        TianjiSiExecutionBundleService.verifyFrozenBundle(actor, selected, bundle);
        assertEquals(PROMPT_TEXT, bundle.instruction(selected.getTemplateKey(), selected.getConfiguration().instructionRef()));
        var nextVersion = withFixedInstruction(template(2));
        assertThrows(AgentLedgerException.class,
                () -> TianjiSiExecutionBundleService.verifyFrozenBundle(actor, nextVersion, bundle));
    }

    private static AgentRuntimeTemplateCommand withFixedInstruction(AgentRuntimeTemplateCommand template) {
        var config = template.getConfiguration();
        var instruction = config.instructionRef();
        return template.setConfiguration(new AgentRuntimeTemplateCommand.TemplateConfigurationVO(
                new AgentRuntimeTemplateCommand.TemplateDependencyRefVO(instruction.key(), instruction.version(), bodyDigest(PROMPT_SPEC)),
                config.modelRef(), config.toolRefs(), config.agenticPlan()));
    }

    private static FrozenSiBundleVO executionFixture(TrustedAgentActor actor, AgentRuntimeTemplateCommand template) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        var config = template.getConfiguration();
        var reference = config.instructionRef();
        String rootSpec = canonicalSpec(Map.of("modelKey", config.modelRef(), "systemInstruction", PROMPT_TEXT));
        String tenantId = Long.toString(actor.tenantId());
        String agentsResourceId = TEMPLATE.equals(template.getTemplateKey()) ? "501" : "502";
        long agentVersionBase = TEMPLATE.equals(template.getTemplateKey()) ? 600 : 700;
        var root = new VersionIdentityVO(tenantId, agentsResourceId, Long.toString(agentVersionBase + template.getTemplateVersion()),
                Long.toString(template.getTemplateVersion()), DdcSiKindEnum.AGENT, "test", bodyDigest(rootSpec));
        var prompt = new VersionIdentityVO(tenantId, "503", Long.toString(800 + reference.version()),
                Long.toString(reference.version()), DdcSiKindEnum.PROMPT, "test", bodyDigest(PROMPT_SPEC));
        var manifest = DdcSiDependencyManifestVO.create(root, null, List.of(prompt),
                List.of(new EdgeVO(root.versionId(), prompt.versionId())));
        return new FrozenSiBundleVO("process", "fusion", "test", null, "tianji", manifest, List.of(
                new SiResourceVO(root, template.getTemplateKey(), null, rootSpec),
                new SiResourceVO(prompt, reference.key(), null, PROMPT_SPEC)));
    }

    private static String canonicalSpec(Map<String, String> fields) {
        return new String(ProcessTypedJson.canonicalBytes(fields), StandardCharsets.UTF_8);
    }

    private static String bodyDigest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    static final class AgentBoundary implements AutoCloseable {
        private final TrustedAgentActor owner = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE,
                BRIDGE_ACTOR.subjectId(), ISSUER);
        private final JdbcTemplate observer;
        private final AnnotationConfigApplicationContext outboxContext = new AnnotationConfigApplicationContext();
        private final AnnotationConfigApplicationContext mappers = new AnnotationConfigApplicationContext();
        private final ScheduledThreadPoolExecutor control = new ScheduledThreadPoolExecutor(1);
        private final java.util.concurrent.ExecutorService requests = Executors.newFixedThreadPool(4);
        private final HttpClient transport = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        private final HttpServer agentServer;
        private final HttpServer processServer;
        private final HttpLinkedAgentClient client;
        private final HttpAgentResultClient receiver;
        private final AgentResultDeliveryHandler delivery;
        private final AgentLedgerService ledger;
        private final AgentRuntimeExecutionService runtime;
        private final OutboxStore messages;
        private final AgentLinkedSubmissionService submissions;
        private final List<TianjiLinkedDispatchProperties.Definition> definitions = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final AtomicReference<String> runId = new AtomicReference<>();
        private final AtomicReference<LinkedAgentRuntimeCommand> linked = new AtomicReference<>();
        private final AtomicReference<Runnable> dropResponse = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> output = new AtomicReference<>();
        private final AtomicReference<Throwable> asynchronousFailure = new AtomicReference<>();
        private final AtomicReference<MockMvc> processMvc = new AtomicReference<>();
        private final AtomicInteger submitCalls = new AtomicInteger();
        private AgentTerminalEventV2 event;

        AgentBoundary(ProcessBoundary process) throws Exception {
            this(process, AS_URL, PS_URL);
        }

        AgentBoundary(ProcessBoundary process, String agentUrl, String processUrl) throws Exception {
            var url = isolatedUrl(agentUrl);
            assertNotEquals(isolatedUrl(processUrl), url, "independent Agent database");
            var runtimeRole = System.getProperty("tianji.agent.outbox.postgres.test.runtime-user", "");
            assertTrue(runtimeRole.matches("tianji_it_[a-z0-9_]{1,44}"));
            var source = install(url, runtimeRole);
            observer = new JdbcTemplate(dataSource(url, null));
            observer.setQueryTimeout(10);
            var manager = new DataSourceTransactionManager(source);
            connectInbox(process);
            processServer = loopback(requests);
            processServer.createContext("/api/tianji/v1/agent-result-inbox",
                    exchange -> relay(exchange, processMvc.get(), "agent-producer-token", null));
            processServer.start();
            receiver = new HttpAgentResultClient(uri(processServer), transport, tenant -> {
                assertEquals(20, tenant);
                return "agent-producer-token";
            }, HTTP_TIMEOUT);
            var reference = new AtomicReference<AgentLedgerService>();
            delivery = new AgentResultDeliveryHandler(reference::get,
                    List.of(new AgentResultDeliveryHandler.Route(DESTINATION, SOURCE, owner, receiver)), Clock.systemUTC());
            configureOutbox(source, manager, delivery);
            var publisher = new OutboxAgentTerminalEventPublisher(source, outboxContext.getBean(OutboxInfrastructure.class),
                    outboxContext.getBean(TransactionalOutbox.class), PRODUCER, Map.of(SOURCE, DESTINATION));
            ledger = AgentLedgerService.withTerminalEvents(source, publisher);
            reference.set(ledger);
            messages = outboxContext.getBean(OutboxStore.class);
            submissions = new AgentLinkedSubmissionService(source, ledger);
            mappers.scan(TianjiHostContextConverter.class.getPackageName());
            mappers.refresh();
            var executor = mock(AgentExecutor.class);
            when(executor.execute(any(), any())).thenAnswer(call -> {
                AgentExecutionCommand command = call.getArgument(0);
                assertArrayEquals(ProcessTypedJson.canonicalBytes(linked.get().getInput()), ProcessTypedJson.canonicalBytes(command.getInput()));
                var handle = mock(AgentExecutionHandle.class);
                when(handle.completion()).thenReturn(CompletableFuture.completedFuture(new AgentExecutionResult(
                        runId.get(), AgentExecutionResult.StatusEnum.COMPLETED, output.get(), null,
                        AgentExecutionResult.SideEffectOutcomeEnum.NONE, ledger.databaseNow())));
                return handle;
            });
            runtime = new AgentRuntimeExecutionService(ledger, (request, boundary) -> AgentRunAuthorization.Decision.ALLOW, executor,
                    mappers.getBean(TianjiHostContextConverter.class), control,
                    new AgentRuntimeCallReceiptStore(source, new AgentRuntimeBindingStore(source)), new TransactionTemplate(manager));
            agentServer = loopback(requests);
            agentServer.createContext("/api/tianji", exchange -> {
                try {
                    var uri = exchange.getRequestURI().getPath();
                    boolean submitting = LinkedAgentRuntimeController.PATH.equals(uri);
                    if (submitting) submitCalls.incrementAndGet();
                    relay(exchange, agentMvc(), "process-bridge-token", submitting ? dropResponse.getAndSet(null) : null);
                } catch (Exception | AssertionError failure) {
                    asynchronousFailure.compareAndSet(null, failure);
                    exchange.close();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            agentServer.start();
            client = new HttpLinkedAgentClient(uri(agentServer), transport, ignored -> "process-bridge-token", HTTP_TIMEOUT);
        }

        AgentRuntimeTemplateCommand publish(long version) {
            return publish(template(version));
        }

        AgentRuntimeTemplateCommand publish(AgentRuntimeTemplateCommand template) {
            withFixedInstruction(template);
            ledger.publishRuntimeTemplate(owner, template, () -> true, (actor, observed) -> deadline -> true);
            definitions.add(new TianjiLinkedDispatchProperties.Definition(template.getTemplateKey(), template.getTemplateVersion(), template.contentSha256()));
            return template;
        }

        void revoke(AgentRuntimeTemplateCommand template) {
            assertTrue(definitions.remove(new TianjiLinkedDispatchProperties.Definition(template.getTemplateKey(),
                    template.getTemplateVersion(), template.contentSha256())));
        }

        void connectInbox(ProcessBoundary process) {
            var access = mock(TianjiProcessAccess.class);
            when(access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API)).thenReturn(PRODUCER_ACTOR);
            processMvc.set(MockMvcBuilders.standaloneSetup(new AgentResultInboxController(process.inbox, access)).build());
        }

        HttpLinkedAgentClient client() { return client; }
        String eventId() { return event.eventId(); }
        AgentRunStatusEnum eventStatus() { return event.status(); }
        int runCount() { return observer.queryForObject("SELECT count(*) FROM tianji_agent.agent_run", Integer.class); }
        int submissions() { return submitCalls.get(); }
        LinkedAgentRuntimeCommand linkedCommand() { return linked.get(); }

        void loseNextSubmitResponse(Runnable point) { dropResponse.set(point); }

        AgentResultReceipt executeAndDeliver(Map<String, Object> newOutput) {
            output.set(newOutput);
            var query = new GetAgentRunQuery(runId.get());
            assertNotNull(runtime.executeQueued(owner, query));
            event = ledger.terminalReferenceV2(owner, query, Duration.ofSeconds(5)).orElseThrow();
            var message = messages.claimDue(1, "fusion-outbox", Duration.ofSeconds(20)).getFirst();
            assertEquals("2", message.schemaVersion());
            var deliveryContext = new DeliveryContext(message.messageId(), message.channel(), message.destination(),
                    message.payload(), message.contentType(), message.schemaVersion(), message.headers(), message.traceId(),
                    message.attemptCount(), message.maxAttempts(), Instant.now().plusSeconds(15));
            assertEquals(DeliveryResult.Kind.SUCCESS, delivery.deliver(deliveryContext).kind());
            assertTrue(messages.markSucceeded(message.id(), "fusion-outbox", 0));
            return repeatDelivery();
        }

        AgentResultReceipt repeatDelivery() { return receiver.receiveV2(event); }

        private MockMvc agentMvc() {
            var issuer = URI.create(ISSUER);
            var registry = mock(TrustedIssuerRegistry.class);
            when(registry.findByIssuer(ISSUER)).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(
                    issuer, URI.create("https://idp.test/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT,
                    "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
            var now = Instant.now();
            var service = new ServiceIdentityPrincipal(owner.subjectId(), "20", owner.subjectId(), "token",
                    URI.create("https://agent.test/api"), 1, Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE,
                            TianjiAgentPermissions.LINKED_RESOLVE_SCOPE, TianjiAgentPermissions.LINKED_CLOSE_SCOPE),
                    "business", "app", "test", "credential", now.minusSeconds(10), now.plusSeconds(60), "app-id", ServiceTokenContext.TENANT);
            SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service, List.of(), issuer));
            var authority = mock(PermissionExecutionGuard.class);
            var access = new TianjiAgentAccess(authority, Clock.systemUTC());
            var binding = new TianjiLinkedDispatchProperties.Binding(issuer, owner.tenantId(), owner.subjectId(), "app-id",
                    "business", "app", "test", SOURCE, Set.of(WorkerScopeTypeEnum.BPMN), List.copyOf(definitions));
            var properties = new TianjiLinkedDispatchProperties(true, false, List.of(binding));
            var bindings = new TianjiLinkedDispatchBindings(properties, registry, Clock.systemUTC());
            var grantIssuer = mock(TianjiServiceExecutionGrantIssuer.class);
            org.mockito.Mockito.doAnswer(call -> {
                top.egon.cola.platform.tianji.contract.agent.AgentRunDTO run = call.getArgument(4);
                linked.set(call.getArgument(3));
                runId.set(run.runId());
                return null;
            }).when(grantIssuer).ensureRuntime(any(), any(), any(), any(), any());
            var bundleService = mock(TianjiSiExecutionBundleService.class);
            when(bundleService.prepare(any(), any())).thenAnswer(call -> executionFixture(call.getArgument(0), call.getArgument(1)));
            var factory = new StaticListableBeanFactory(Map.of("grantIssuer", grantIssuer, "executionBundle", bundleService));
            var runtimeController = new LinkedAgentRuntimeController(submissions, access, bindings,
                    new MockEnvironment().withProperty("stellar.tianji.agent.runtime-v2.enabled", "true"),
                    factory.getBeanProvider(TianjiServiceExecutionGrantIssuer.class),
                    factory.getBeanProvider(TianjiSiExecutionBundleService.class));
            var legacyController = new LinkedAgentDispatchController(submissions, access, bindings, properties,
                    factory.getBeanProvider(TianjiLinkedServiceAdmission.class));
            return MockMvcBuilders.standaloneSetup(runtimeController, legacyController)
                    .addFilters(new VerifiedAgentIssuerFilter()).setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice(access)).build();
        }

        private void relay(HttpExchange exchange, MockMvc mvc, String token, Runnable beforeDropping) throws IOException {
            try (exchange) {
                if (!("Bearer " + token).equals(exchange.getRequestHeaders().getFirst("Authorization"))
                        || !"POST".equals(exchange.getRequestMethod()) || exchange.getRequestURI().getQuery() != null) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                var input = exchange.getRequestBody().readNBytes(140 * 1024 + 1);
                if (input.length > 140 * 1024) {
                    exchange.sendResponseHeaders(413, -1);
                    return;
                }
                var request = post(exchange.getRequestURI()).contentType(MediaType.APPLICATION_JSON).content(input);
                exchange.getRequestHeaders().forEach((name, values) -> request.header(name, values.toArray()));
                var handled = mvc.perform(request).andReturn();
                var result = handled.getResponse();
                if (result.getStatus() >= 500 && handled.getResolvedException() != null) {
                    asynchronousFailure.compareAndSet(null, handled.getResolvedException());
                }
                if (beforeDropping != null && result.getStatus() == 202) {
                    beforeDropping.run();
                    return;
                }
                var response = result.getContentAsByteArray();
                if (result.getContentType() != null) exchange.getResponseHeaders().set("Content-Type", result.getContentType());
                result.getHeaders("Cache-Control").forEach(value -> exchange.getResponseHeaders().add("Cache-Control", value));
                exchange.sendResponseHeaders(result.getStatus(), response.length);
                exchange.getResponseBody().write(response);
            } catch (Exception | AssertionError failure) {
                asynchronousFailure.compareAndSet(null, failure);
            }
        }

        private void configureOutbox(DataSource source, DataSourceTransactionManager manager, DeliveryHandler handler) {
            outboxContext.registerBean(DataSource.class, () -> source);
            outboxContext.registerBean(PlatformTransactionManager.class, () -> manager);
            outboxContext.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
            outboxContext.registerBean(DeliveryHandler.class, () -> handler);
            outboxContext.registerBean(OutboxAfterCommitBuffer.class, () -> mock(OutboxAfterCommitBuffer.class));
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(outboxContext,
                    "egon.cola.component.transactional-outbox.enabled=true",
                    "egon.cola.component.transactional-outbox.storage.type=JDBC",
                    "egon.cola.component.transactional-outbox.storage.jdbc.schema=tianji_agent",
                    "egon.cola.component.transactional-outbox.polling.enabled=false",
                    "egon.cola.component.transactional-outbox.cleanup.enabled=false",
                    "egon.cola.component.transactional-outbox.annotation.enabled=false",
                    "egon.cola.component.transactional-outbox.state-machine.execution-timeout=PT1S",
                    "egon.cola.component.transactional-outbox.state-machine.claim-evaluation-timeout=PT3S");
            outboxContext.register(TransactionalOutboxAutoConfiguration.class, OutboxJdbcAutoConfiguration.class,
                    OutboxStateMachineAutoConfiguration.class);
            outboxContext.refresh();
        }

        private static DataSource install(String url, String role) {
            var source = dataSource(url, null);
            var owner = new JdbcTemplate(source);
            owner.setQueryTimeout(10);
            requireRestrictedRole(owner, role);
            var vector = System.getProperty("tianji.runtime.safety.postgres.vector.version", "");
            assertTrue(vector.matches("[0-9]+[.][0-9]+[.][0-9]+"));
            assertEquals(vector, owner.queryForObject("""
                    SELECT e.extversion FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
                    WHERE e.extname='vector' AND n.nspname='public'
                    """, String.class));
            assertEquals(0, owner.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, AS_SCHEMA));
            var agent = Flyway.configure().dataSource(source).schemas(AS_SCHEMA).defaultSchema(AS_SCHEMA)
                    .locations("classpath:db/tianji-agent").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                    .target("17").cleanDisabled(true).load();
            assertEquals(17, agent.migrate().migrationsExecuted);
            agent.validate();
            var outbox = Flyway.configure().dataSource(source).schemas(AS_SCHEMA).defaultSchema(AS_SCHEMA)
                    .table("tianji_agent_outbox_schema_history").baselineOnMigrate(true).baselineVersion("0")
                    .locations("classpath:db/transactional-outbox/postgresql").cleanDisabled(true).load();
            assertEquals(1, outbox.migrate().migrationsExecuted);
            outbox.validate();
            owner.execute("GRANT USAGE ON SCHEMA " + AS_SCHEMA + " TO " + role);
            grant(owner, AS_SCHEMA, role, "SELECT", "flyway_schema_history", "tianji_agent_outbox_schema_history",
                    "agent_session", "agent_session_event", "agent_session_scope_state", "agent_session_lease",
                    "agent_run_execution_grant", "agent_budget_reservation", "agent_tool_action_intent",
                    "agent_tool_capability", "agent_tool_invocation");
            grant(owner, AS_SCHEMA, role, "SELECT,INSERT", "agent_definition", "agent_definition_version", "agent_event");
            grant(owner, AS_SCHEMA, role, "SELECT,INSERT,UPDATE", "agent_run", "agent_process_dispatch",
                    "agent_run_command_receipt", "egon_cola_outbox_message", "agent_runtime_binding", "agent_runtime_call_receipt");
            owner.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA " + AS_SCHEMA + " TO " + role);
            return dataSource(url, role);
        }

        private static HttpServer loopback(java.util.concurrent.Executor executor) throws IOException {
            var listener = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
            listener.setExecutor(executor);
            return listener;
        }

        private static URI uri(HttpServer server) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override public void close() {
            client.close();
            receiver.close();
            agentServer.stop(0);
            processServer.stop(0);
            transport.shutdownNow();
            requests.shutdownNow();
            control.shutdownNow();
            mappers.close();
            outboxContext.close();
            if (asynchronousFailure.get() != null) fail("Loopback fixture request failed", asynchronousFailure.get());
        }
    }

    private static void assertInput(Map<String, Object> input) {
        assertArrayEquals(ProcessTypedJson.canonicalBytes(Map.of("claim", Map.of("amount", 17L))),
                ProcessTypedJson.canonicalBytes(input));
    }
}
