package top.egon.cola.platform.tianji.agent.server.security;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianji.agent.server.api.AgentApiErrors;
import top.egon.cola.platform.tianji.agent.server.api.AgentRequestBodyAdvice;
import top.egon.cola.platform.tianji.agent.server.api.LinkedAgentDispatchController;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named = "tianji.linked.http.postgres.test.url", matches = ".+")
@Timeout(90)
class LinkedAgentDispatchHttpPostgresTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void protected_adapter_uses_real_fences_and_recovers_from_admission_failure() throws Exception {
        String url = System.getProperty("tianji.linked.http.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("linked HTTP test requires a fresh loopback database");
        }
        var source = new DriverManagerDataSource(url); var jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("linked HTTP test schema already exists");
        }
        var flyway = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").cleanDisabled(true).load();
        flyway.migrate(); flyway.validate();
        var ledger = new AgentLedgerService(source); var submissions = new AgentLinkedSubmissionService(source, ledger);
        URI issuer = URI.create("https://idp.example/tenant/20");
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", issuer.toString());
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                5, 0, 1000, 500, new BigDecimal("2.00"), "USD", 2, Set.of(), null, null);
        var definition = ledger.publish(actor, new AgentDefinitionSpecDTO("review", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), policy,
                Map.of("chatModelBeanName", "fixture", "modelName", "fixture-model", "rootAgentName", "writer",
                        "agents", List.of(Map.of("name", "writer", "instruction", "write")))));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var principal = new ServiceIdentityPrincipal("worker", "20", "worker", "token-id", URI.create("https://api.example/tianji"), 1,
                Set.of(TianjiAgentPermissions.LINKED_SUBMIT_SCOPE, TianjiAgentPermissions.LINKED_RESOLVE_SCOPE, TianjiAgentPermissions.LINKED_CLOSE_SCOPE),
                "xingyuan", "worker-app", "test", "credential-id", now.minusSeconds(30), now.plusSeconds(300), "app-id", ServiceTokenContext.TENANT);
        SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(principal, List.of(), issuer));
        var issuers = mock(TrustedIssuerRegistry.class);
        when(issuers.findByIssuer(issuer.toString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(issuer,
                URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
        var definitionRule = new TianjiLinkedDispatchProperties.Definition("review", 1, definition.contentSha256());
        var binding = new TianjiLinkedDispatchProperties.Binding(issuer, 20, "worker", "app-id", "xingyuan", "worker-app", "test",
                "process-east", Set.of(WorkerScopeTypeEnum.BPMN), List.of(definitionRule));
        var properties = new TianjiLinkedDispatchProperties(true, true, List.of(binding));
        var guard = mock(PermissionExecutionGuard.class);
        var access = new TianjiAgentAccess(guard, Clock.systemUTC());
        var calls = new AtomicInteger();
        TianjiLinkedServiceAdmission admission = (authorized, identity, configured, command, run) -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("unavailable fixture");
        };
        var factory = new DefaultListableBeanFactory(); factory.registerSingleton("admission", admission);
        var controller = new LinkedAgentDispatchController(submissions, access,
                new TianjiLinkedDispatchBindings(properties, issuers, Clock.systemUTC()), properties,
                factory.getBeanProvider(TianjiLinkedServiceAdmission.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new VerifiedAgentIssuerFilter())
                .setControllerAdvice(new AgentApiErrors(), new AgentRequestBodyAdvice()).build();
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-1", "occurrence-1",
                definition.contentSha256(), now.plusSeconds(60));
        var command = new LinkedAgentStartCommand(new StartAgentRunCommand("review", 1, null, "intent-1", null, "trace-1",
                new AgentInputDTO("input", List.of(), Map.of()), null), link);
        var query = new LinkedAgentLookupQuery("intent-1", link); var json = JsonMapper.builder().build();
        String base = "/api/tianji/v1/agent-process-dispatches/";
        mvc.perform(post(base + "submit").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command)))
                .andExpect(status().isServiceUnavailable());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_process_dispatch WHERE idempotency_key='intent-1'", Integer.class));
        mvc.perform(post(base + "resolve").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(query)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.found").value(true)).andExpect(jsonPath("$.submission.closed").value(false));
        mvc.perform(post(base + "submit").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command)))
                .andExpect(status().isAccepted());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run WHERE idempotency_key='intent-1'", Integer.class));
        mvc.perform(post(base + "close").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(query)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.submission.closed").value(true))
                .andExpect(jsonPath("$.submission.run.status").value("CANCELLED"));
        mvc.perform(post(base + "submit").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(command)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_DISPATCH_CLOSED"));
        verify(guard, atLeastOnce()).requireExecution(TianjiAgentPermissions.LINKED_SUBMIT_API);

        var http = mock(java.net.http.HttpClient.class);
        when(http.followRedirects()).thenReturn(java.net.http.HttpClient.Redirect.NEVER);
        when(http.<byte[]>sendAsync(any(), any())).thenAnswer(invocation -> {
            java.net.http.HttpRequest request = invocation.getArgument(0);
            java.net.http.HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
            var response = mvc.perform(post(request.uri().getRawPath()).contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", request.headers().firstValue("Authorization").orElseThrow())
                    .content(readBody(request))).andReturn().getResponse();
            @SuppressWarnings("unchecked") java.net.http.HttpResponse<byte[]> result = mock(java.net.http.HttpResponse.class);
            when(result.statusCode()).thenReturn(response.getStatus());
            var info = mock(java.net.http.HttpResponse.ResponseInfo.class); when(info.statusCode()).thenReturn(response.getStatus());
            var subscriber = handler.apply(info);
            subscriber.onSubscribe(new java.util.concurrent.Flow.Subscription() { public void request(long count) { } public void cancel() { } });
            subscriber.onNext(List.of(java.nio.ByteBuffer.wrap(response.getContentAsByteArray()))); subscriber.onComplete();
            byte[] bytes = subscriber.getBody().toCompletableFuture().join(); when(result.body()).thenReturn(bytes);
            return java.util.concurrent.CompletableFuture.completedFuture(result);
        });
        var sdkLink = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-1", "occurrence-sdk",
                definition.contentSha256(), now.plusSeconds(60));
        var sdkCommand = new LinkedAgentStartCommand(new StartAgentRunCommand("review", 1, null, "intent-sdk", null, "trace-sdk",
                new AgentInputDTO("input", List.of(), Map.of()), null), sdkLink);
        var sdkQuery = new LinkedAgentLookupQuery("intent-sdk", sdkLink);
        try (var sdk = new top.egon.cola.platform.tianji.client.HttpLinkedAgentClient(URI.create("https://api.example"), http,
                operation -> "service-fixture", Duration.ofSeconds(5))) {
            var started = sdk.submit(sdkCommand);
            assertEquals(started.run().runId(), sdk.resolve(sdkQuery).submission().run().runId());
            assertEquals(top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.CANCELLED,
                    sdk.close(sdkQuery).submission().run().status());
        }

        var context = new ApplicationContextRunner().withUserConfiguration(TianjiLinkedDispatchConfiguration.class, LinkedAgentDispatchController.class)
                .withPropertyValues("stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.linked.enabled=true",
                        "stellar.tianji.agent.linked.bindings[0].issuer=" + issuer,
                        "stellar.tianji.agent.linked.bindings[0].tenant-id=20", "stellar.tianji.agent.linked.bindings[0].client-id=worker",
                        "stellar.tianji.agent.linked.bindings[0].app-id=app-id", "stellar.tianji.agent.linked.bindings[0].business-code=xingyuan",
                        "stellar.tianji.agent.linked.bindings[0].application-code=worker-app", "stellar.tianji.agent.linked.bindings[0].environment=test",
                        "stellar.tianji.agent.linked.bindings[0].source-id=process-east", "stellar.tianji.agent.linked.bindings[0].scope-types[0]=BPMN",
                        "stellar.tianji.agent.linked.bindings[0].definitions[0].key=review", "stellar.tianji.agent.linked.bindings[0].definitions[0].version=1",
                        "stellar.tianji.agent.linked.bindings[0].definitions[0].content-sha256=" + definition.contentSha256())
                .withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger)
                .withBean(TrustedIssuerRegistry.class, () -> issuers).withBean(TianjiAgentAccess.class, () -> access);
        context.run(value -> assertThat(value).hasNotFailed().hasSingleBean(AgentLinkedSubmissionService.class));
        context.withPropertyValues("stellar.tianji.agent.linked.submission-enabled=true")
                .run(value -> assertThat(value).hasFailed());
        context.withPropertyValues("stellar.tianji.agent.linked.submission-enabled=true")
                .withBean(TianjiLinkedServiceAdmission.class, () -> admission)
                .run(value -> assertThat(value).hasNotFailed().hasSingleBean(LinkedAgentDispatchController.class));
    }

    private static byte[] readBody(java.net.http.HttpRequest request) {
        var result = new java.util.concurrent.CompletableFuture<byte[]>(); var output = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer bytes) { byte[] value = new byte[bytes.remaining()]; bytes.get(value); output.writeBytes(value); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(output.toByteArray()); }
        });
        return result.join();
    }
}
