package top.egon.cola.platform.tianji.process.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3BearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterAutoConfiguration;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpBearerAuthenticationFilter;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpEndpointAuthenticationPolicy.Requirement;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ProcessStorageConfigurationTest {
    @Test
    void identity_and_outbox_share_the_unique_jackson_2_mapper() {
        var source = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(source);
        var infrastructure = new OutboxInfrastructure(source, "source", manager, "manager");
        var properties = new TransactionalOutboxProperties();
        properties.getStorage().getJdbc().setSchema("tianji_process");

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(Jackson2AutoConfiguration.class))
                .withUserConfiguration(ProcessStorageConfiguration.class, IdentityMapperConsumerConfiguration.class)
                .withInitializer(context -> context.addBeanFactoryPostProcessor(
                        IdpStarterAutoConfiguration.idpJacksonObjectMapperAlias()))
                .withBean(OutboxInfrastructure.class, () -> infrastructure)
                .withBean(TransactionalOutboxProperties.class, () -> properties)
                .withPropertyValues("flowable.database-schema-update=false", "flowable.database-schema=tianji_process",
                        "spring.sql.init.mode=never", "spring.flyway.enabled=false",
                        "egon.cola.component.transactional-outbox.storage.type=JDBC",
                        "egon.cola.component.transactional-outbox.storage.jdbc.schema=tianji_process")
                .run(context -> {
                    assertNull(context.getStartupFailure(), () -> String.valueOf(context.getStartupFailure()));
                    var outbox = context.getBean("outboxObjectMapper", ObjectMapper.class);
                    assertEquals(1, context.getBeanNamesForType(ObjectMapper.class).length);
                    assertSame(outbox, context.getBeanProvider(ObjectMapper.class).getIfUnique());
                    assertSame(outbox, context.getBean(IdentityMapperConsumer.class).mapper());
                });
    }

    @Test
    void automation_readiness_uses_the_existing_engine_source_and_rejects_unready_v14_when_enabled() {
        var engine = mock(ProcessEngine.class);
        var spring = mock(SpringProcessEngineConfiguration.class);
        var source = mock(DataSource.class);
        when(engine.getProcessEngineConfiguration()).thenReturn(spring);
        when(spring.getDatabaseSchema()).thenReturn("tianji_process");
        when(spring.getDataSource()).thenReturn(new TransactionAwareDataSourceProxy(source));
        var configuration = new ProcessSecurityConfiguration();
        var compatible = safeEnvironment();
        var enabled = safeEnvironment().withProperty("stellar.tianji.process.automation.enabled", "true")
                .withProperty("stellar.tianji.process.api.enabled", "true")
                .withProperty("stellar.tianji.process.api.start-receipt-read-enabled", "true");
        var missingRecovery = safeEnvironment().withProperty("stellar.tianji.process.automation.enabled", "true")
                .withProperty("stellar.tianji.process.api.enabled", "true");
        var internalOnly = safeEnvironment().withProperty("stellar.tianji.process.automation.enabled", "true");
        var hostVerifier = mock(ProcessApiSchemaVerifier.class);

        try (var verifiers = mockConstruction(ProcessApiSchemaVerifier.class, (verifier, context) -> {
            assertSame(source, context.arguments().getFirst());
            assertEquals("tianji_process", context.arguments().get(1));
            doThrow(new IllegalStateException("V14 storage unavailable")).when(verifier).verifyAutomationV14();
        })) {
            assertDoesNotThrow(() -> guard(compatible));
            var verifier = configuration.processApiSchemaVerifier(engine);
            verify(verifier).verify();
            verify(verifier, never()).verifyAutomationV14();

            assertThrows(IllegalStateException.class,
                    () -> configuration.processAutomationStorageReadiness(engine, missingRecovery).afterSingletonsInstantiated());
            assertEquals(1, verifiers.constructed().size());
            for (var environment : java.util.List.of(enabled, internalOnly)) {
                assertThrows(IllegalStateException.class,
                        () -> configuration.processAutomationStorageReadiness(engine, environment).afterSingletonsInstantiated());
                verify(verifiers.constructed().getLast()).verifyAutomationV14();
            }
            assertEquals(3, verifiers.constructed().size());
            verifyNoInteractions(source, hostVerifier);
        }
    }

    @Test
    void rejects_automatic_schema_changes_or_unvalidated_outbox_before_initializing_engines() {
        assertDoesNotThrow(() -> guard(safeEnvironment()));
        for (String[] override : new String[][] {
                {"flowable.database-schema-update", "true"},
                {"spring.sql.init.mode", "always"},
                {"spring.flyway.enabled", "true"},
                {"egon.cola.component.transactional-outbox.enabled", "false"},
                {"egon.cola.component.transactional-outbox.storage.type", "MYBATIS_PLUS"},
                {"egon.cola.component.transactional-outbox.storage.validate-schema", "false"},
                {"egon.cola.component.transactional-outbox.storage.jdbc.schema", "other_schema"},
                {"flowable.database-schema", "bad.schema"}
        }) {
            MockEnvironment environment = safeEnvironment().withProperty(override[0], override[1]);
            assertThrows(IllegalStateException.class, () -> guard(environment), override[0]);
        }
    }

    @Test
    void rejects_a_different_engine_datasource_or_transaction_manager() {
        DataSource source = mock(DataSource.class);
        DataSource other = mock(DataSource.class);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        OutboxInfrastructure outbox = new OutboxInfrastructure(source, "source", manager, "manager");
        TransactionalOutboxProperties properties = new TransactionalOutboxProperties();
        properties.getStorage().getJdbc().setSchema("tianji_process");
        var configurer = new ProcessStorageConfiguration().processStorageGuard(outbox, properties);
        SpringProcessEngineConfiguration engine = new SpringProcessEngineConfiguration();
        engine.setDatabaseSchema("tianji_process");
        engine.setDatabaseSchemaUpdate("false");
        engine.setDataSource(other);
        engine.setTransactionManager(manager);
        assertThrows(IllegalStateException.class, () -> configurer.configure(engine));
        engine.setDataSource(source);
        engine.setTransactionManager(new DataSourceTransactionManager(source));
        assertThrows(IllegalStateException.class, () -> configurer.configure(engine));
    }

    @Test
    void worker_and_inbox_enablement_require_an_enabled_servlet_process_api() {
        var config = new ProcessStorageConfiguration();
        var disabled = new MockEnvironment();
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(disabled, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        var enabled = new MockEnvironment().withProperty("stellar.tianji.process.worker.enabled", "true")
                .withProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(enabled, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(enabled, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        enabled.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(enabled, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        var inbox = new MockEnvironment().withProperty("stellar.tianji.process.agent-results.enabled", "true")
                .withProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(inbox, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(inbox, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        inbox.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(inbox, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        var tasks = new MockEnvironment().withProperty("stellar.tianji.process.human-tasks.enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(tasks, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(tasks, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        tasks.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(tasks, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        var publication = new MockEnvironment().withProperty("stellar.tianji.process.human-tasks.publish-enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(publication, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        var approvals = new MockEnvironment().withProperty("stellar.tianji.process.approval-verification.enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(approvals, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> config.workerWebBoundaryGuard(approvals, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        approvals.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class, () -> config.workerWebBoundaryGuard(approvals, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
    }

    @Test
    void case_readiness_and_new_starts_have_independent_fail_closed_feature_boundaries() {
        var configuration = new ProcessStorageConfiguration();
        var servlet = mock(WebApplicationContext.class);
        for (var environment : java.util.List.of(
                new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true"),
                new MockEnvironment().withProperty("stellar.tianji.process.cases.new-starts-enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true").withProperty("stellar.tianji.process.human-tasks.enabled", "true"),
                new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true").withProperty("stellar.tianji.process.cases.new-starts-enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true"))) {
            assertThrows(IllegalStateException.class, () -> configuration.workerWebBoundaryGuard(environment, servlet).afterSingletonsInstantiated());
        }
        var reads = new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true").withProperty("stellar.tianji.process.api.enabled", "true");
        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(reads, servlet).afterSingletonsInstantiated());
        assertThrows(IllegalStateException.class, () -> configuration.workerWebBoundaryGuard(reads, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        reads.setProperty("stellar.tianji.process.cases.new-starts-enabled", "true"); reads.setProperty("stellar.tianji.process.human-tasks.enabled", "true");
        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(reads, servlet).afterSingletonsInstantiated());
    }

    @Test
    void linked_case_factory_holds_typed_lazy_providers_without_resolving_application_services_at_engine_configuration() {
        var source = mock(DataSource.class); var manager = new DataSourceTransactionManager(source);
        var outbox = new OutboxInfrastructure(source, "source", manager, "manager");
        var properties = new TransactionalOutboxProperties(); properties.getStorage().getJdbc().setSchema("tianji_process");
        @SuppressWarnings("unchecked") var services = (org.springframework.beans.factory.ObjectProvider<top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService>) mock(org.springframework.beans.factory.ObjectProvider.class);
        @SuppressWarnings("unchecked") var registries = (org.springframework.beans.factory.ObjectProvider<top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry>) mock(org.springframework.beans.factory.ObjectProvider.class);
        var service = mock(top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService.class);
        var registry = mock(top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.class);
        org.mockito.Mockito.when(services.getObject()).thenReturn(service); org.mockito.Mockito.when(registries.getObject()).thenReturn(registry);
        for (boolean enabled : new boolean[]{false, true}) {
            var engine = new SpringProcessEngineConfiguration(); engine.setDataSource(source); engine.setTransactionManager(manager);
            engine.setDatabaseSchema("tianji_process"); engine.setDatabaseSchemaUpdate("false");
            var arguments = new java.util.ArrayList<java.util.List<?>>();
            try (var statements = org.mockito.Mockito.mockConstruction(org.springframework.jdbc.core.JdbcTemplate.class,
                    (value, ignored) -> org.mockito.Mockito.when(value.queryForObject("SELECT current_schema()", String.class)).thenReturn("tianji_process"));
                 var factories = org.mockito.Mockito.mockConstruction(top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory.class,
                    (value, construction) -> arguments.add(construction.arguments()))) {
                new ProcessStorageConfiguration().processStorageGuard(outbox, properties, new MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", Boolean.toString(enabled)), services, registries).configure(engine);
                org.mockito.Mockito.verifyNoInteractions(services, registries);
                org.junit.jupiter.api.Assertions.assertEquals(1, arguments.size());
                org.junit.jupiter.api.Assertions.assertEquals(enabled ? 2 : 0, arguments.getFirst().size());
                if (enabled) {
                    org.junit.jupiter.api.Assertions.assertSame(service, ((java.util.function.Supplier<?>) arguments.getFirst().get(0)).get());
                    org.junit.jupiter.api.Assertions.assertSame(registry, ((java.util.function.Supplier<?>) arguments.getFirst().get(1)).get());
                    org.mockito.Mockito.clearInvocations(services, registries);
                }
            }
        }
    }

    @Test
    void cancellation_requires_the_enabled_servlet_process_api() {
        var configuration = new ProcessStorageConfiguration();
        var enabled = new MockEnvironment().withProperty("stellar.tianji.process.cancellation.enabled", "true");
        assertThrows(IllegalStateException.class, () -> configuration.workerWebBoundaryGuard(enabled, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        enabled.setProperty("stellar.tianji.process.api.enabled", "true");
        assertThrows(IllegalStateException.class, () -> configuration.workerWebBoundaryGuard(enabled, mock(ApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(enabled, mock(WebApplicationContext.class)).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(new MockEnvironment(), mock(ApplicationContext.class)).afterSingletonsInstantiated());
    }

    @Test
    void worker_recovery_requires_api_worker_and_servlet() {
        var configuration = new ProcessStorageConfiguration();
        var servlet = mock(WebApplicationContext.class);
        var nonServlet = mock(ApplicationContext.class);
        var enabled = new MockEnvironment().withProperty("stellar.tianji.process.worker-recovery.enabled", "true")
                .withProperty("stellar.tianji.process.api.enabled", "true");

        assertThrows(IllegalStateException.class,
                () -> configuration.workerWebBoundaryGuard(enabled, servlet).afterSingletonsInstantiated());
        enabled.setProperty("stellar.tianji.process.worker.enabled", "false");
        assertThrows(IllegalStateException.class,
                () -> configuration.workerWebBoundaryGuard(enabled, servlet).afterSingletonsInstantiated());
        enabled.setProperty("stellar.tianji.process.worker.enabled", "true");
        assertThrows(IllegalStateException.class,
                () -> configuration.workerWebBoundaryGuard(enabled, nonServlet).afterSingletonsInstantiated());
        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(enabled, servlet).afterSingletonsInstantiated());
        enabled.setProperty("stellar.tianji.process.api.enabled", "false");
        assertThrows(IllegalStateException.class,
                () -> configuration.workerWebBoundaryGuard(enabled, servlet).afterSingletonsInstantiated());

        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(new MockEnvironment(), nonServlet)
                .afterSingletonsInstantiated());
        var disabled = new MockEnvironment().withProperty("stellar.tianji.process.worker-recovery.enabled", "false");
        assertDoesNotThrow(() -> configuration.workerWebBoundaryGuard(disabled, nonServlet).afterSingletonsInstantiated());
    }

    @Test
    void migration_preflight_defaults_off() {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var process = section(yaml.getObject(), "stellar", "tianji", "process");
        Object flag = process.get("migration-preflight");
        assertEquals(Boolean.FALSE, flag instanceof Map<?, ?> mapping ? mapping.get("enabled") : null);
    }

    @Test
    void migration_preflight_requires_enabled_servlet_api() {
        var configuration = ProcessSecurityConfiguration.MigrationPreflightConfiguration.class;
        var servlet = configuration.getAnnotation(ConditionalOnWebApplication.class);
        assertEquals(ConditionalOnWebApplication.Type.SERVLET, servlet.type());
        var condition = configuration.getAnnotation(ConditionalOnProperty.class);
        assertEquals("stellar.tianji.process", condition.prefix());
        assertEquals(List.of("api.enabled", "migration-preflight.enabled"), List.of(condition.name()));
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());

        var factory = Arrays.stream(configuration.getDeclaredMethods())
                .filter(method -> "processMigrationPreflightService".equals(method.getName())).findFirst().orElseThrow();
        assertEquals(ProcessMigrationPreflightService.class, factory.getReturnType());
        assertEquals(List.of("processMigrationPreflightService"), List.of(factory.getAnnotation(Bean.class).value()));
    }

    @Test
    void worker_recovery_defaults_off_and_declares_only_the_exact_user_scope() {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        var process = section(properties, "stellar", "tianji", "process");
        assertEquals(Boolean.FALSE, section(process, "worker-recovery").get("enabled"));
        assertEquals(Boolean.FALSE, section(process, "cancellation").get("enabled"));

        var scopes = section(properties, "egon", "cola", "platform", "tianquan", "jianshen",
                "user-scope-policy", "permission-scopes");
        assertEquals(List.of("tianji:process:worker-recover"), scopes.get("TIANJI_PROCESS_WORKER_RECOVER"));
        assertEquals(List.of("tianji:process:read"), scopes.get("TIANJI_PROCESS_READ"));
        assertEquals(List.of("tianji:process:cancel"), scopes.get("TIANJI_PROCESS_CANCEL"));
    }

    @Test
    void recovery_security_transport_normalizes_only_the_two_exact_operations() throws Exception {
        var filter = new ProcessSecurityConfiguration.ClosedProcessSecurityErrors(false, false, true);
        String base = "/api/tianji/v1/process-instances/p-1";
        String failures = base + "/worker-failures";
        String recoveries = base + "/worker-recoveries";
        String challenge = "Bearer realm=process";
        String origin = "https://console.example";
        var errors = Map.of(401, "TIANJI_UNAUTHENTICATED", 403, "TIANJI_PERMISSION_DENIED",
                503, "TIANJI_DEPENDENCY_UNAVAILABLE");
        var json = JsonMapper.builder().build();
        for (var error : errors.entrySet()) {
            int status = error.getKey();
            var response = new MockHttpServletResponse();
            var request = recoveryRequest(status == 401 ? "GET" : "POST", status == 401 ? failures : recoveries);
            filter.doFilter(request, response, (ignored, downstream) -> {
                var http = (HttpServletResponse) downstream;
                http.setStatus(status);
                http.setContentType("application/problem+json");
                http.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                if (status == 401) {
                    http.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
                    http.getWriter().write("{\"diagnostic\":\"private-challenge\"}");
                } else {
                    http.getOutputStream().write("private-transport".getBytes(StandardCharsets.UTF_8));
                }
            });
            assertEquals(status, response.getStatus());
            assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
            assertEquals(origin, response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
            if (status == 401) assertEquals(challenge, response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
            assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())));
            var body = json.readTree(response.getContentAsByteArray());
            assertEquals(Set.of("code", "correlationId"), Set.copyOf(body.propertyNames()));
            assertEquals(error.getValue(), body.get("code").asString());
            assertTrue(body.get("correlationId").isNull());
        }

        for (var request : List.of(recoveryRequest("POST", failures), recoveryRequest("GET", recoveries),
                recoveryRequest("GET", base + "/control"), recoveryRequest("GET", failures + "/extra"))) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (ignored, downstream) -> {
                var http = (HttpServletResponse) downstream;
                http.setStatus(403);
                http.setContentType(MediaType.TEXT_PLAIN_VALUE);
                http.getOutputStream().write("original-error".getBytes(StandardCharsets.UTF_8));
            });
            assertEquals(403, response.getStatus());
            assertEquals(MediaType.TEXT_PLAIN_VALUE, response.getContentType());
            assertEquals("original-error", response.getContentAsString(StandardCharsets.UTF_8));
        }

        var success = new MockHttpServletResponse();
        filter.doFilter(recoveryRequest("GET", failures), success, (ignored, downstream) -> {
            var http = (HttpServletResponse) downstream;
            http.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            http.setContentType(MediaType.APPLICATION_JSON_VALUE);
            http.getOutputStream().write("{\"instanceId\":\"p-1\"}".getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(200, success.getStatus());
        assertEquals("no-store", success.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("{\"instanceId\":\"p-1\"}", success.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void installs_the_closed_filter_for_enabled_process_api_even_with_worker_recovery_disabled() throws Exception {
        var beans = new StaticListableBeanFactory(Map.of(
                "identity", mock(IdpBearerAuthenticationFilter.class),
                "authorization", mock(Rbac3BearerAuthenticationFilter.class),
                "guard", mock(PermissionExecutionGuard.class)));
        var http = mock(HttpSecurity.class, RETURNS_SELF);
        new ProcessSecurityConfiguration().processSecurityFilterChain(http,
                new MockEnvironment().withProperty("stellar.tianji.process.api.enabled", "true")
                        .withProperty("egon.cola.platform.tianquan.shoubing.register-filter", "false")
                        .withProperty("egon.cola.platform.tianquan.jianshen.register-filter", "false"),
                beans.getBeanProvider(IdpBearerAuthenticationFilter.class),
                beans.getBeanProvider(Rbac3BearerAuthenticationFilter.class),
                beans.getBeanProvider(PermissionExecutionGuard.class));

        var filter = org.mockito.ArgumentCaptor.forClass(Filter.class);
        verify(http).addFilterBefore(filter.capture(), eq(RequestContextFilter.class));
        var selected = assertInstanceOf(ProcessSecurityConfiguration.ClosedProcessSecurityErrors.class, filter.getValue());
        assertFalse(selected.shouldNotFilter(recoveryRequest("GET", "/api/tianji/v1/process-instance-correlations")));
        assertTrue(selected.shouldNotFilter(recoveryRequest("GET", "/api/tianji/v1/process-instances/p-1/worker-failures")));
    }

    @Test
    void owner_read_gets_emit_the_exact_closed_security_envelope() throws Exception {
        var filter = new ProcessSecurityConfiguration.ClosedProcessSecurityErrors(true, true, false);
        for (String path : ownerReadPaths()) {
            for (var error : Map.of(401, "TIANJI_UNAUTHENTICATED", 403, "TIANJI_PERMISSION_DENIED",
                    503, "TIANJI_DEPENDENCY_UNAVAILABLE").entrySet()) {
                int status = error.getKey();
                var request = new MockHttpServletRequest("GET", "/edge" + path);
                request.setContextPath("/edge");
                request.setServletPath(path);
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (ignored, downstream) -> {
                    var http = (HttpServletResponse) downstream;
                    http.setStatus(status);
                    http.setContentType("application/problem+json");
                    http.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=process");
                    http.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://console.example");
                    http.getWriter().write("{\"code\":\"private-detail\"}");
                });

                assertEquals(status, response.getStatus());
                assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL), path);
                assertEquals("Bearer realm=process", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
                assertEquals("https://console.example", response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
                assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())));
                assertEquals("{\"code\":\"" + error.getValue() + "\",\"correlationId\":null}",
                        response.getContentAsString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void owner_read_heads_emit_get_error_metadata_without_a_body() throws Exception {
        var filter = new ProcessSecurityConfiguration.ClosedProcessSecurityErrors(true, true, false);
        for (String path : ownerReadPaths()) {
            var response = new MockHttpServletResponse();
            filter.doFilter(recoveryRequest("HEAD", path), response, (ignored, downstream) -> {
                var http = (HttpServletResponse) downstream;
                http.setStatus(401);
                http.getWriter().write("{\"code\":\"TIANJI_UNAUTHENTICATED\"}");
            });
            assertEquals(401, response.getStatus());
            assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL), path);
            assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(response.getContentType())));
            assertEquals("{\"code\":\"TIANJI_UNAUTHENTICATED\",\"correlationId\":null}".length(), response.getContentLength());
            assertEquals(0, response.getContentAsByteArray().length);
        }
    }

    @Test
    void owner_read_envelope_preserves_disabled_paths_unrelated_methods_and_other_statuses() throws Exception {
        var disabled = new ProcessSecurityConfiguration.ClosedProcessSecurityErrors(false, false, false);
        assertTrue(disabled.shouldNotFilter(recoveryRequest("GET", "/api/tianji/v1/process-start-receipts")));
        assertTrue(disabled.shouldNotFilter(recoveryRequest("HEAD", "/api/tianji/v1/process-instances/p-1/agent-outcomes")));
        assertFalse(disabled.shouldNotFilter(recoveryRequest("GET", "/api/tianji/v1/process-instance-correlations")));

        var all = new ProcessSecurityConfiguration.ClosedProcessSecurityErrors(true, true, true);
        for (String path : ownerReadPaths()) {
            assertTrue(all.shouldNotFilter(recoveryRequest("POST", path)), path);
            assertTrue(all.shouldNotFilter(recoveryRequest("GET", path + "/extra")), path);
        }
        assertTrue(all.shouldNotFilter(recoveryRequest("HEAD", "/api/tianji/v1/process-instances/p-1/worker-failures")));
        assertTrue(all.shouldNotFilter(recoveryRequest("GET", "/api/tianji/v1/process-instances/p-1/control")));

        for (int status : List.of(200, 404, 409)) {
            var response = new MockHttpServletResponse();
            all.doFilter(recoveryRequest("GET", "/api/tianji/v1/process-instances/p-1/agent-outcomes"), response,
                    (ignored, downstream) -> {
                        var http = (HttpServletResponse) downstream;
                        http.setStatus(status);
                        http.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        http.getWriter().write("{\"original\":true}");
                    });
            assertEquals(status, response.getStatus());
            assertEquals("{\"original\":true}", response.getContentAsString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void event_routes_select_the_contract_bearer_principals() {
        var policy = new ProcessSecurityConfiguration().processEndpointAuthenticationPolicy();
        assertAll(
                () -> assertEquals(Requirement.SERVICE,
                        policy.requirement(recoveryRequest("POST", "/api/tianji/v2/process-events")), "API-007"),
                () -> assertEquals(Requirement.USER_OR_SERVICE,
                        policy.requirement(recoveryRequest("GET", "/api/tianji/v2/process-event-receipts/er-1")), "API-008"),
                () -> assertEquals(Requirement.USER,
                        policy.requirement(recoveryRequest("GET", "/api/tianji/v2/process-event-receipts/er-1/extra"))),
                () -> assertEquals(Requirement.USER_OR_SERVICE,
                        policy.requirement(recoveryRequest("GET", "/api/tianji/v1/process-start-receipts"))),
                () -> assertEquals(Requirement.SERVICE,
                        policy.requirement(recoveryRequest("POST", "/api/tianji/v1/worker-tasks/acquire"))));
    }

    private static List<String> ownerReadPaths() {
        return List.of("/api/tianji/v1/process-instance-correlations",
                "/api/tianji/v1/process-instances/p-1/agent-outcomes",
                "/api/tianji/v1/process-start-receipts");
    }

    private static MockHttpServletRequest recoveryRequest(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private static Map<?, ?> section(Map<?, ?> source, String... path) {
        Map<?, ?> section = source;
        for (String key : path) {
            section = assertInstanceOf(Map.class, section.get(key), "Missing YAML section " + key);
        }
        return section;
    }

    private static void guard(MockEnvironment environment) {
        ProcessStorageConfiguration.processStoragePropertiesGuard(environment).postProcessBeanFactory(null);
    }

    private static MockEnvironment safeEnvironment() {
        return new MockEnvironment().withProperty("flowable.database-schema-update", "false")
                .withProperty("flowable.database-schema", "tianji_process")
                .withProperty("spring.sql.init.mode", "never")
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("egon.cola.component.transactional-outbox.storage.type", "JDBC")
                .withProperty("egon.cola.component.transactional-outbox.storage.jdbc.schema", "tianji_process");
    }

    @Configuration(proxyBeanMethods = false)
    static class IdentityMapperConsumerConfiguration {
        @Bean
        IdentityMapperConsumer identityMapperConsumer(@Qualifier("jacksonObjectMapper") ObjectMapper mapper) {
            return new IdentityMapperConsumer(mapper);
        }
    }

    record IdentityMapperConsumer(ObjectMapper mapper) { }
}
