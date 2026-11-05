package top.egon.cola.platform.tianji.agent.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentModelUsageCorrelationQuery;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentModelUsagePageDTO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.AgentModelUsageRunDTO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentModelUsageDiagnosticsTest {
    private static final String PREFIX = "stellar.tianji.agent.";
    private static final String STORAGE = PREFIX + "storage.enabled=true";
    private static final String USAGE = PREFIX + "storage.model-usage-enabled=true";
    private static final String ROUTE = "expense@1";
    private static final String ROUTES = PREFIX + "storage.model-usage-cumulative-route-refs[0]=" + ROUTE;
    private static final String API = PREFIX + "api.enabled=true";
    private static final Instant NOW = Instant.parse("2026-11-05T05:50:00.000001Z");
    private static final TrustedAgentActor OWNER = new TrustedAgentActor(42, TrustedAgentActor.SubjectType.SERVICE,
            "owner", "a".repeat(64));
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(TianjiAgentStorageConfiguration.class);

    @Test
    void exactGetAndListAuthorization() {
        var ledger = mock(AgentLedgerService.class);
        var access = mock(TianjiAgentAccess.class);
        var diagnostics = new AgentModelUsageDiagnostics(ledger, access);
        var run = new GetAgentRunQuery("run-1");
        var correlation = new AgentModelUsageCorrelationQuery("corr", NOW.minusSeconds(1), NOW, null);
        var runUsage = new AgentModelUsageRunDTO(run.runId(), "corr", NOW.minusSeconds(1), NOW, List.of(), null);
        var page = new AgentModelUsagePageDTO(List.of(runUsage), null, NOW);
        var authorized = new TianjiAgentAccess.Authorized(OWNER, NOW.plusSeconds(30));
        when(access.require(TianjiAgentPermissions.GET_API)).thenReturn(authorized);
        when(access.require(TianjiAgentPermissions.LIST_API)).thenReturn(authorized);
        when(ledger.getModelUsage(OWNER, run, "slot-2")).thenReturn(runUsage);
        when(ledger.listModelUsageByCorrelation(OWNER, correlation)).thenReturn(page);

        assertSame(runUsage, diagnostics.byRun(run, "slot-2"));
        assertSame(page, diagnostics.byCorrelation(correlation));
        var calls = inOrder(access, ledger);
        calls.verify(access).require(TianjiAgentPermissions.GET_API);
        calls.verify(ledger).getModelUsage(OWNER, run, "slot-2");
        calls.verify(access).require(TianjiAgentPermissions.LIST_API);
        calls.verify(ledger).listModelUsageByCorrelation(OWNER, correlation);
        calls.verifyNoMoreInteractions();

        assertFalse(Modifier.isPublic(AgentModelUsageDiagnostics.class.getModifiers()));
        assertFalse(AnnotatedElementUtils.hasAnnotation(AgentModelUsageDiagnostics.class, RequestMapping.class));
        for (var method : AgentModelUsageDiagnostics.class.getDeclaredMethods()) {
            assertFalse(AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class));
        }
    }

    @Test
    void accessDenialDoesNotReachLedger() {
        var ledger = mock(AgentLedgerService.class);
        var access = mock(TianjiAgentAccess.class);
        var diagnostics = new AgentModelUsageDiagnostics(ledger, access);
        doThrow(new AccessDeniedException("denied")).when(access).require(TianjiAgentPermissions.GET_API);
        doThrow(new AccessDeniedException("denied")).when(access).require(TianjiAgentPermissions.LIST_API);
        assertThrows(AccessDeniedException.class, () -> diagnostics.byRun(new GetAgentRunQuery("run-1"), null));
        assertThrows(AccessDeniedException.class, () -> diagnostics.byCorrelation(
                new AgentModelUsageCorrelationQuery("corr", NOW.minusSeconds(1), NOW, null)));
        verifyNoInteractions(ledger);
    }

    @Test
    void storageOffDoesNotReadV15() {
        context.run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(DataSource.class)
                .doesNotHaveBean(AgentLedgerService.class).doesNotHaveBean(AgentModelUsageDiagnostics.class));
        context.withPropertyValues(USAGE, ROUTES).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("TIANJI_AGENT_MODEL_USAGE_REQUIRES_STORAGE");
        });
        var created = new AtomicInteger();
        context.withPropertyValues(USAGE, ROUTES).withBean(DataSource.class, () -> {
            created.incrementAndGet();
            return mock(DataSource.class);
        }).run(ctx -> assertThat(ctx).hasFailed());
        assertEquals(0, created.get());

        var source = mock(DataSource.class);
        try (var verifiers = mockConstruction(AgentStorageVerifier.class)) {
            context.withPropertyValues(STORAGE, ROUTES).withBean(DataSource.class, () -> source).run(ctx -> {
                assertThat(ctx).hasNotFailed().doesNotHaveBean(AgentModelUsageDiagnostics.class);
                assertFalse(ctx.getBean(AgentLedgerService.class).modelUsageEnabled());
            });
            assertEquals(1, verifiers.constructed().size());
            verify(verifiers.constructed().getFirst()).verify();
            verify(verifiers.constructed().getFirst(), never()).verifyModelUsage();
        }
        verifyNoInteractions(source);
    }

    @Test
    void verifiedOptInOnly() {
        var source = mock(DataSource.class);
        var publisher = mock(AgentTerminalEventPublisher.class);
        when(publisher.usesDataSource(source)).thenReturn(true);
        when(publisher.producerId()).thenReturn("fixture-producer");
        var access = mock(TianjiAgentAccess.class);

        try (var verifiers = mockConstruction(AgentStorageVerifier.class)) {
            new WebApplicationContextRunner().withUserConfiguration(TianjiAgentStorageConfiguration.class)
                    .withPropertyValues(STORAGE, USAGE, ROUTES, API, PREFIX + "runtime.enabled=false")
                    .withBean(DataSource.class, () -> source).withBean(AgentTerminalEventPublisher.class, () -> publisher)
                    .withBean(TianjiAgentAccess.class, () -> access).run(ctx -> {
                        assertThat(ctx).hasNotFailed().hasSingleBean(AgentModelUsageDiagnostics.class);
                        var ledger = ctx.getBean(AgentLedgerService.class);
                        assertTrue(ledger.usesDataSource(source));
                        assertTrue(ledger.usesModelUsageRoutes(List.of(ROUTE)));
                        assertTrue(ledger.hasTerminalPublisher(publisher));
                    });
            assertEquals(2, verifiers.constructed().size());
            verify(verifiers.constructed().get(0)).verifyModelUsage();
            verify(verifiers.constructed().get(1)).verify();

            new WebApplicationContextRunner().withUserConfiguration(TianjiAgentStorageConfiguration.class)
                    .withPropertyValues(STORAGE, USAGE, ROUTES).withBean(DataSource.class, () -> source)
                    .run(ctx -> {
                        assertThat(ctx).hasNotFailed().doesNotHaveBean(AgentModelUsageDiagnostics.class);
                        assertTrue(ctx.getBean(AgentLedgerService.class).modelUsageEnabled());
                    });
            context.withPropertyValues(STORAGE, USAGE, ROUTES, API).withBean(DataSource.class, () -> source)
                    .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(AgentModelUsageDiagnostics.class));
        }
        verifyNoInteractions(source, access);
    }

    @Test
    void customLedgerMustMatchSourceAndExactReviewedRoutes() {
        var source = mock(DataSource.class);
        var custom = mock(AgentLedgerService.class);
        var configured = context.withPropertyValues(STORAGE, USAGE, ROUTES)
                .withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> custom);
        configured.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("TIANJI_AGENT_STORAGE_DATA_SOURCE_MISMATCH");
        });
        when(custom.usesDataSource(source)).thenReturn(true);
        configured.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("TIANJI_AGENT_MODEL_USAGE_CONFIGURATION_MISMATCH");
        });
        when(custom.usesModelUsageRoutes(List.of(ROUTE))).thenReturn(true);
        try (var verifiers = mockConstruction(AgentStorageVerifier.class)) {
            configured.run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertSame(custom, ctx.getBean(AgentLedgerService.class));
            });
            assertEquals(1, verifiers.constructed().size());
            verify(verifiers.constructed().getFirst()).verify();
        }
        verifyNoInteractions(source);
    }

    @Test
    void rejectsMissingAndDuplicateReviewedRoutes() {
        var source = mock(DataSource.class);
        context.withBean(DataSource.class, () -> source).withPropertyValues(STORAGE, USAGE).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("Agent model usage routes are invalid");
        });
        context.withBean(DataSource.class, () -> source)
                .withPropertyValues(STORAGE, USAGE, ROUTES, PREFIX + "storage.model-usage-cumulative-route-refs[1]=" + ROUTE)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasStackTraceContaining("Agent model usage routes are invalid");
                });
        verifyNoInteractions(source);
    }

    @Test
    void applicationYamlKeepsOnlyTheTwoDefaultOffUsageValues() {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Map<?, ?> root = yaml.getObject();
        var stellar = assertInstanceOf(Map.class, root.get("stellar"));
        var tianji = assertInstanceOf(Map.class, stellar.get("tianji"));
        var agent = assertInstanceOf(Map.class, tianji.get("agent"));
        var storage = assertInstanceOf(Map.class, agent.get("storage"));
        assertEquals(Map.of("model-usage-enabled", false, "model-usage-cumulative-route-refs", List.of()), storage);
    }
}
