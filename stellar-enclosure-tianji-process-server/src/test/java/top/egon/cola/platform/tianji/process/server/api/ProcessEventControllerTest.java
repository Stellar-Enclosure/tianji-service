package top.egon.cola.platform.tianji.process.server.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;
import top.egon.cola.platform.tianji.process.server.ProcessSecurityConfiguration;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.event.ProcessEventApplicationService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.AutomationObservationPermitVO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess.AutomationInstanceAccessVO;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest.PrincipalType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProcessEventControllerTest {
    private static final String EVENTS_PATH = "/api/tianji/v2/process-events";
    private static final String RECEIPT_PATH = "/api/tianji/v2/process-event-receipts/{receiptId}";
    private static final String RECEIVE_RESOURCE = "tianji.api.process.event.ingest.v2";
    private static final String RECEIPT_RESOURCE = "tianji.api.process.event.receipt.get.v2";
    private static final String RECEIPT = "er-101";
    private static final String INSTANCE = "instance-101";
    private static final Instant NOW = Instant.parse("2026-11-12T08:40:00Z");
    private static final ProcessActor PRODUCER = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "events",
            "https://idp.test/main");
    private static final ProcessActor OBSERVER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "operator",
            "https://idp.test/main");
    private static final String BODY = """
            {
              "eventId":"evt-101","definitionKey":"orders","routeKey":"order-created","routeVersion":2,
              "type":"OrderCreated","occurredAt":"2026-11-12T08:40:00Z","correlationKey":"order-101",
              "waitToken":null,"payload":{"amount":41}
            }
            """;
    private static final String HIDDEN = """
            {"code":"TIANJI_RESOURCE_NOT_FOUND","correlationId":null}
            """.strip();

    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final ProcessEventApplicationService events = mock(ProcessEventApplicationService.class);
    private final ProcessExecutionService processes = mock(ProcessExecutionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProcessEventController(access, events, processes))
            .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();

    @Test
    void returns_202_only_with_the_durable_receipt_and_never_serializes_the_payload() throws Exception {
        when(access.require(RECEIVE_RESOURCE)).thenReturn(PRODUCER);
        when(events.receive(eq(PRODUCER), any(ProcessEventCommand.class))).thenAnswer(call -> {
            ProcessEventCommand actual = call.getArgument(1);
            assertEquals("evt-101", actual.getEventId());
            assertEquals("orders", actual.getDefinitionKey());
            assertEquals(2, actual.getRouteVersion());
            assertEquals(NOW, actual.getOccurredAt());
            assertEquals("41", actual.getPayload().get("amount").toString());
            return received();
        });

        mvc.perform(post(EVENTS_PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.receiptId").value(RECEIPT)).andExpect(jsonPath("$.state").value("RECEIVED"))
                .andExpect(jsonPath("$.releaseVersion").value("orders-2")).andExpect(jsonPath("$.activationRevision").value(2))
                .andExpect(jsonPath("$.instanceId").value(nullValue())).andExpect(jsonPath("$.payload").doesNotExist())
                .andExpect(jsonPath("$.producerSubject").doesNotExist());
        var ordered = inOrder(access, events);
        ordered.verify(access).require(RECEIVE_RESOURCE);
        ordered.verify(events).receive(eq(PRODUCER), any(ProcessEventCommand.class));
        verifyNoInteractions(processes);
    }

    @Test
    void rejects_non_service_and_malformed_events_before_admission() throws Exception {
        when(access.require(RECEIVE_RESOURCE)).thenReturn(OBSERVER);
        mvc.perform(post(EVENTS_PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        when(access.require(RECEIVE_RESOURCE)).thenReturn(PRODUCER);
        for (String invalid : new String[]{
                BODY.replace("\"waitToken\":null,", ""),
                BODY.replace("\"waitToken\":null", "\"waitToken\":null,\"tenantId\":20"),
                BODY.replace("\"waitToken\":null", "\"waitToken\":null,\"releaseVersion\":\"orders-1\""),
                BODY.replace("\"routeVersion\":2", "\"routeVersion\":2,\"routeVersion\":1"),
                BODY.replace("08:40:00Z", "10:40:00+02:00"),
                BODY.replace("08:40:00Z", "08:40:00.1234560Z")}) {
            mvc.perform(post(EVENTS_PATH).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        var oversized = BODY.replace("\"amount\":41", "\"amount\":\"" + "a".repeat(75 * 1024) + "\"");
        mvc.perform(post(EVENTS_PATH).contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isContentTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        verifyNoInteractions(events, processes);
    }

    @Test
    void preserves_the_stable_conflict_for_a_changed_event_replay() throws Exception {
        when(access.require(RECEIVE_RESOURCE)).thenReturn(PRODUCER);
        when(events.receive(eq(PRODUCER), any(ProcessEventCommand.class))).thenThrow(
                new AutomationOperationException(AutomationOperationException.Code.TIANJI_EVENT_CONFLICT));
        mvc.perform(post(EVENTS_PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_EVENT_CONFLICT"));
    }

    @Test
    void allows_an_unbound_receipt_for_its_producer_without_observing_an_instance() throws Exception {
        when(access.require(RECEIPT_RESOURCE)).thenReturn(PRODUCER);
        when(events.observe(eq(PRODUCER), eq(RECEIPT), any())).thenReturn(received());

        mvc.perform(get(RECEIPT_PATH, RECEIPT)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.receiptId").value(RECEIPT)).andExpect(jsonPath("$.state").value("RECEIVED"))
                .andExpect(jsonPath("$.instanceId").value(nullValue())).andExpect(jsonPath("$.payload").doesNotExist());
        verifyNoInteractions(processes);
    }

    @Test
    void observes_only_the_bound_instance_for_the_same_current_actor_and_hides_denial() throws Exception {
        when(access.require(RECEIPT_RESOURCE)).thenReturn(OBSERVER);
        var broadPermit = new AutomationObservationPermitVO(false, true, true, true, false);
        var metadataOnly = new AutomationObservationPermitVO(false, true, false, false, false);
        when(access.observeAutomationInstance()).thenReturn(new AutomationInstanceAccessVO(OBSERVER, broadPermit));
        when(processes.observeAutomation(OBSERVER, INSTANCE, metadataOnly)).thenReturn(new AutomationInstanceDTO());
        when(events.observe(eq(OBSERVER), eq(RECEIPT), any())).thenAnswer(call -> {
            Predicate<String> observe = call.getArgument(2);
            if (!observe.test(INSTANCE)) throw new ProcessResourceNotFoundException();
            return received().setState("APPLIED").setInstanceId(INSTANCE).setDecidedAt(NOW).setDecisionCode("APPLIED");
        });

        mvc.perform(get(RECEIPT_PATH, RECEIPT)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.instanceId").value(INSTANCE)).andExpect(jsonPath("$.state").value("APPLIED"));
        verify(processes).observeAutomation(OBSERVER, INSTANCE, metadataOnly);

        when(access.observeAutomationInstance()).thenThrow(new AccessDeniedException("private instance"));
        when(events.observe(eq(OBSERVER), eq("er-unknown"), any())).thenThrow(new ProcessResourceNotFoundException());
        for (String hidden : new String[]{RECEIPT, "er-unknown"}) {
            mvc.perform(get(RECEIPT_PATH, hidden)).andExpect(status().isNotFound())
                    .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().json(HIDDEN));
        }
    }

    @Test
    void rejects_body_or_query_parameters_for_receipt_lookup() throws Exception {
        when(access.require(RECEIPT_RESOURCE)).thenReturn(PRODUCER);
        mvc.perform(get(RECEIPT_PATH, RECEIPT).param("tenantId", "20"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        mvc.perform(get(RECEIPT_PATH, RECEIPT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        verifyNoInteractions(events, processes);
    }

    @Test
    void declares_exact_principals_and_permission_alternatives_for_the_two_resources() throws Exception {
        var ingest = ProcessEventController.class.getMethod("receiveProcessEvent", ProcessEventCommand.class, HttpServletRequest.class);
        var ingestResource = ingest.getAnnotation(PermissionResource.class);
        assertEquals(RECEIVE_RESOURCE, ingestResource.key());
        assertEquals(Set.of(PrincipalType.SERVICE), Set.of(ingestResource.allowedPrincipals()));
        assertEquals(Set.of("tianji:process:event:ingest"), Set.of(ingestResource.oauthScopes()));
        assertEquals(Set.of("TIANJI_PROCESS_EVENT_INGEST"), Set.of(ingest.getAnnotation(RequiresPermissions.class).codes()));

        var read = ProcessEventController.class.getMethod("getProcessEventReceipt", String.class, HttpServletRequest.class);
        var readResource = read.getAnnotation(PermissionResource.class);
        var readPermissions = read.getAnnotation(RequiresPermissions.class);
        assertEquals(RECEIPT_RESOURCE, readResource.key());
        assertEquals(Set.of(PrincipalType.USER, PrincipalType.SERVICE), Set.of(readResource.allowedPrincipals()));
        assertEquals(Set.of("tianji:process:event:ingest", "tianji:process:read", "tianji:process:observe"),
                Set.of(readResource.oauthScopes()));
        assertEquals(PermissionMode.ANY, readPermissions.mode());
        assertEquals(Set.of("TIANJI_PROCESS_EVENT_INGEST", "TIANJI_PROCESS_READ", "TIANJI_PROCESS_OBSERVE"),
                Set.of(readPermissions.codes()));
    }

    @Test
    void security_chain_closes_only_the_enabled_event_methods_and_envelopes() throws Exception {
        var identity = mock(IdpBearerAuthenticationFilter.class);
        var authorization = mock(Rbac3BearerAuthenticationFilter.class);
        var permissions = mock(PermissionExecutionGuard.class);
        var issuer = URI.create("https://idp.test/main");
        var resource = URI.create("https://api.test/process");
        var issuedAt = Instant.now().minusSeconds(1);
        var service = new ServiceIdentityPrincipal("events", "20", "events", "service-token", resource, 1,
                Set.of("tianji:process:event:ingest"), "xingyuan", "tianji", "test", "credential", issuedAt, issuedAt.plusSeconds(300));
        installIdentityFilter(identity, service, issuer);
        doAnswer(call -> {
            FilterChain chain = call.getArgument(2);
            chain.doFilter(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(authorization).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        when(events.receive(eq(PRODUCER), any(ProcessEventCommand.class))).thenReturn(received());
        when(events.observe(eq(PRODUCER), eq(RECEIPT), any())).thenReturn(received());

        new WebApplicationContextRunner().withUserConfiguration(EventSecurityWeb.class)
                .withPropertyValues("stellar.tianji.process.api.enabled=true", "stellar.tianji.process.events.enabled=true",
                        "egon.cola.platform.tianquan.shoubing.register-filter=false",
                        "egon.cola.platform.tianquan.jianshen.register-filter=false")
                .withBean(IdpBearerAuthenticationFilter.class, () -> identity)
                .withBean(Rbac3BearerAuthenticationFilter.class, () -> authorization)
                .withBean(PermissionExecutionGuard.class, () -> permissions)
                .withBean("tianjiProcessAccess", TianjiProcessAccess.class, () -> new TianjiProcessAccess(permissions, java.time.Clock.systemUTC()))
                .withBean("processEventApplicationService", ProcessEventApplicationService.class, () -> events)
                .withBean("processExecutionService", ProcessExecutionService.class, () -> processes)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var secure = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).apply(springSecurity()).build();
                    secure.perform(post(EVENTS_PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"))
                            .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.correlationId").value(nullValue()));
                    secure.perform(get(RECEIPT_PATH, RECEIPT)).andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.code").value("TIANJI_UNAUTHENTICATED"));
                    secure.perform(head(RECEIPT_PATH, RECEIPT).header("Authorization", "Bearer producer"))
                            .andExpect(status().isForbidden());
                    secure.perform(put(EVENTS_PATH).header("Authorization", "Bearer producer").contentType(MediaType.APPLICATION_JSON).content(BODY))
                            .andExpect(status().isForbidden());
                    secure.perform(get(RECEIPT_PATH, RECEIPT).header("Authorization", "Bearer producer").accept(MediaType.TEXT_PLAIN))
                            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
                    secure.perform(post(EVENTS_PATH).header("Authorization", "Bearer producer")
                                    .header("X-Egon-Tianji-Tenant", "21").contentType(MediaType.APPLICATION_JSON).content(BODY))
                            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TIANJI_TENANT_CONTEXT_CHANGED"));
                    verifyNoInteractions(events, processes);
                    secure.perform(post(EVENTS_PATH).header("Authorization", "Bearer producer")
                                    .contentType(MediaType.APPLICATION_JSON).content(BODY))
                            .andExpect(status().isAccepted()).andExpect(jsonPath("$.receiptId").value(RECEIPT));
                    secure.perform(get(RECEIPT_PATH, RECEIPT).header("Authorization", "Bearer producer")).andExpect(status().isOk());
                    doThrow(new AccessDeniedException("no current receipt grant")).when(permissions).requireExecution(RECEIPT_RESOURCE);
                    secure.perform(get(RECEIPT_PATH, RECEIPT).header("Authorization", "Bearer producer"))
                            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"))
                            .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.correlationId").value(nullValue()));
                });
    }

    private static void installIdentityFilter(IdpBearerAuthenticationFilter filter, ServiceIdentityPrincipal service, URI issuer)
            throws Exception {
        doAnswer(call -> {
            HttpServletRequest request = call.getArgument(0);
            if ("Bearer producer".equals(request.getHeader("Authorization"))) {
                SecurityContextHolder.getContext().setAuthentication(new IdpAuthenticationToken(service, List.of(), issuer));
            }
            FilterChain chain = call.getArgument(2);
            chain.doFilter(request, call.getArgument(1));
            return null;
        }).when(filter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    private static ProcessEventReceiptDTO received() {
        return new ProcessEventReceiptDTO().setReceiptId(RECEIPT).setEventId("evt-101").setState("RECEIVED")
                .setReleaseVersion("orders-2").setActivationRevision(2).setAdmittedAt(NOW).setReceivedAt(NOW);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProcessEventController.class, ProcessApiErrors.class, ProcessRequestBodyAdvice.class})
    static class EventSecurityWeb {
        @Bean
        SecurityFilterChain eventTestSecurity(HttpSecurity http, Environment environment,
                ObjectProvider<IdpBearerAuthenticationFilter> identity, ObjectProvider<Rbac3BearerAuthenticationFilter> authorization,
                ObjectProvider<PermissionExecutionGuard> permissions) {
            var method = Objects.requireNonNull(ReflectionUtils.findMethod(ProcessSecurityConfiguration.class,
                    "processSecurityFilterChain", HttpSecurity.class, Environment.class, ObjectProvider.class, ObjectProvider.class,
                    ObjectProvider.class));
            ReflectionUtils.makeAccessible(method);
            return (SecurityFilterChain) ReflectionUtils.invokeMethod(method, new ProcessSecurityConfiguration(), http, environment,
                    identity, authorization, permissions);
        }

        @Bean
        HandlerExceptionResolver processEventMediaErrors() {
            var method = Objects.requireNonNull(ReflectionUtils.findMethod(ProcessSecurityConfiguration.class, "processEventMediaErrors"));
            ReflectionUtils.makeAccessible(method);
            return (HandlerExceptionResolver) ReflectionUtils.invokeMethod(method, new ProcessSecurityConfiguration());
        }
    }
}
