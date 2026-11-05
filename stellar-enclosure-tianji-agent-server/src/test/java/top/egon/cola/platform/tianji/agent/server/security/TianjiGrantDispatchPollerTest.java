package top.egon.cola.platform.tianji.agent.server.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunReconciler;
import top.egon.cola.platform.tianji.agent.autoconfigure.AgentReconciliationProperties;
import top.egon.cola.platform.tianji.agent.autoconfigure.TianjiAgentReconciliationAutoConfiguration;
import top.egon.cola.platform.tianji.agent.domain.GrantReadyAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiGrantDispatchPollerTest {
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AgentRunDispatcher dispatcher = mock(AgentRunDispatcher.class);
    private final TianjiExecutionGrantAuthorizer authorizer = mock(TianjiExecutionGrantAuthorizer.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
    private final AtomicReference<Runnable> callback = new AtomicReference<>();
    private TianjiGrantDispatchPoller poller;

    @BeforeEach void setup() {
        when(dispatcher.usesAuthorization(authorizer)).thenReturn(true);
        when(dispatcher.usesLedger(ledger)).thenReturn(true);
        when(authorizer.usesLedger(ledger)).thenReturn(true);
        when(dispatcher.awaitQuiescence(any())).thenReturn(true);
        doAnswer(invocation -> { callback.set(invocation.getArgument(0)); return scheduled; })
                .when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        when(dispatcher.dispatch(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        poller = new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler, 2, 2,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }
    @AfterEach void cleanup() { poller.close(); }

    @Test void rotates_pages_and_wraps_even_when_prior_runs_are_still_queued() {
        var first = candidate(21, "00000000-0000-0000-0000-000000000121");
        var second = candidate(22, "00000000-0000-0000-0000-000000000122");
        var third = candidate(23, "00000000-0000-0000-0000-000000000123");
        when(ledger.grantReadyRuns(null, 2)).thenReturn(List.of(first, second));
        when(ledger.grantReadyRuns(second.cursor(), 2)).thenReturn(List.of(third));
        when(ledger.grantReadyRuns(third.cursor(), 2)).thenReturn(List.of());
        poller.start(); poller.start();
        callback.get().run(); callback.get().run(); callback.get().run(); callback.get().run();
        verify(scheduler, times(1)).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(1)));
        verify(ledger, times(2)).grantReadyRuns(null, 2);
        verify(ledger).grantReadyRuns(second.cursor(), 2); verify(ledger).grantReadyRuns(third.cursor(), 2);
        verify(dispatcher, times(2)).dispatch(first.actor(), first.query());
        verify(dispatcher).dispatch(third.actor(), third.query());
        poller.stop(); assertFalse(poller.isRunning()); verify(scheduled).cancel(false); verify(dispatcher).close();
        assertThrows(IllegalStateException.class, poller::start);
    }

    @Test void service_discovery_is_enabled_only_for_the_matching_service_authorizer() {
        when(authorizer.supportsService()).thenReturn(true);
        poller.close();
        poller = new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler, 2, 2,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        var actor = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/tenants/21");
        var candidate = new GrantReadyAgentRun(actor, new GetAgentRunQuery("00000000-0000-0000-0000-000000000123"),
                Instant.parse("2026-10-29T00:00:00Z"));
        when(ledger.grantReadyRuns(null, 2, true)).thenReturn(List.of(candidate));
        poller.start(); callback.get().run();
        verify(ledger).grantReadyRuns(null, 2, true);
        verify(ledger, never()).grantReadyRuns(any(), anyInt());
        verify(dispatcher).dispatch(actor, candidate.query());
    }

    @Test void admission_failures_do_not_pin_the_cursor_or_expose_details() {
        var first = candidate(21, "00000000-0000-0000-0000-000000000121");
        var second = candidate(22, "00000000-0000-0000-0000-000000000122");
        when(ledger.grantReadyRuns(null, 2)).thenReturn(List.of(first, second));
        doThrow(new java.util.concurrent.RejectedExecutionException("private-detail")).when(dispatcher).dispatch(first.actor(), first.query());
        poller.start(); callback.get().run(); callback.get().run();
        verify(dispatcher).dispatch(second.actor(), second.query());
        verify(ledger).grantReadyRuns(second.cursor(), 2);
    }

    @Test void in_flight_limit_stops_admission_and_resumes_from_the_same_cursor() {
        var first = candidate(21, "00000000-0000-0000-0000-000000000121");
        var second = candidate(22, "00000000-0000-0000-0000-000000000122");
        var pending = new CompletableFuture<top.egon.cola.platform.tianji.contract.agent.AgentRunDTO>();
        when(ledger.grantReadyRuns(null, 2)).thenReturn(List.of(first, second));
        when(ledger.grantReadyRuns(first.cursor(), 2)).thenReturn(List.of(second));
        when(dispatcher.dispatch(first.actor(), first.query())).thenReturn(pending);
        poller.close();
        poller = new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler, 2, 1, Duration.ofSeconds(1), Duration.ZERO);
        poller.start(); callback.get().run(); callback.get().run();
        verify(dispatcher, times(1)).dispatch(first.actor(), first.query());
        verify(dispatcher, never()).dispatch(second.actor(), second.query());
        pending.complete(null); callback.get().run();
        verify(dispatcher, times(1)).dispatch(second.actor(), second.query());
    }

    @Test void overlapping_poll_and_a_stale_callback_after_stop_cannot_dispatch() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var candidate = candidate(21, "00000000-0000-0000-0000-000000000121");
        when(ledger.grantReadyRuns(null, 2)).thenAnswer(inv -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); return List.of(candidate); });
        poller.close();
        poller = new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler, 2, 2, Duration.ofSeconds(1), Duration.ZERO);
        poller.start(); Runnable task = callback.get();
        Thread active = Thread.ofPlatform().daemon().start(task);
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); task.run(); verify(ledger, times(1)).grantReadyRuns(null, 2);
            poller.stop(); release.countDown(); active.join(5000); assertFalse(active.isAlive()); task.run();
            verify(dispatcher, never()).dispatch(any(), any()); verify(ledger, times(1)).grantReadyRuns(null, 2);
        } finally { release.countDown(); }
    }

    @Test void mismatched_authorizer_and_missing_dependencies_fail_closed() {
        when(dispatcher.usesAuthorization(authorizer)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer,
                scheduler, 2, 2, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        when(dispatcher.usesAuthorization(authorizer)).thenReturn(true);
        when(dispatcher.usesLedger(ledger)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer,
                scheduler, 2, 2, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        when(dispatcher.usesLedger(ledger)).thenReturn(true);
        when(authorizer.usesLedger(ledger)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer,
                scheduler, 2, 2, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(TianjiGrantDispatchConfiguration.class)
                .run(context -> assertTrue(context.getBeansOfType(TianjiGrantDispatchPoller.class).isEmpty()));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner().withUserConfiguration(TianjiGrantDispatchConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.dispatch.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test void automatic_dispatch_requires_reconciliation_for_the_same_ledger() {
        var configuration = new TianjiGrantDispatchConfiguration();
        var registries = new StaticListableBeanFactory().getBeanProvider(MeterRegistry.class);
        var matching = new AgentRunReconciler(ledger, mock(TaskScheduler.class), new AgentReconciliationProperties());
        var other = new AgentRunReconciler(mock(AgentLedgerService.class), mock(TaskScheduler.class), new AgentReconciliationProperties());
        var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("stellar.tianji.agent.execution-authorization.enabled", "true");
        assertThrows(IllegalStateException.class,
                () -> configuration.tianjiGrantDispatchPoller(ledger, dispatcher, authorizer, matching, scheduler, environment, registries));
        environment.setProperty("stellar.tianji.agent.reconciliation.enabled", "true");
        assertThrows(IllegalStateException.class,
                () -> configuration.tianjiGrantDispatchPoller(ledger, dispatcher, authorizer, other, scheduler, environment, registries));
        var configured = configuration.tianjiGrantDispatchPoller(ledger, dispatcher, authorizer, matching, scheduler, environment, registries);
        assertTrue(matching.getPhase() < configured.getPhase());
        configured.close();
    }

    @Test void a_reconciliation_start_failure_prevents_dispatch_from_starting() {
        var reconcileScheduler = mock(TaskScheduler.class);
        doThrow(new IllegalStateException("unavailable")).when(reconcileScheduler)
                .scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(TianjiAgentReconciliationAutoConfiguration.class))
                .withUserConfiguration(TianjiGrantDispatchConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.dispatch.enabled=true", "stellar.tianji.agent.execution-authorization.enabled=true",
                        "stellar.tianji.agent.reconciliation.enabled=true")
                .withBean(AgentLedgerService.class, () -> ledger)
                .withBean(AgentRunDispatcher.class, () -> dispatcher)
                .withBean(TianjiExecutionGrantAuthorizer.class, () -> authorizer)
                .withBean("tianjiAgentDispatchScheduler", TaskScheduler.class, () -> scheduler)
                .withBean("tianjiAgentReconciliationScheduler", TaskScheduler.class, () -> reconcileScheduler)
                .run(context -> assertNotNull(context.getStartupFailure()));
        verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    void backlog_samples_after_discovery_and_dispatch_and_never_on_scrape() {
        var registry = new SimpleMeterRegistry();
        var clock = new AtomicLong();
        var observation = new AgentQueuedBacklogObservation(ledger, () -> registry, clock::get);
        var run = candidate(21, "00000000-0000-0000-0000-000000000121");
        when(ledger.grantReadyRuns(null, 2)).thenReturn(List.of(run));
        when(ledger.grantReadyRuns(run.cursor(), 2)).thenReturn(List.of());
        when(ledger.sampleQueuedBacklog()).thenReturn(0, 257);
        poller.close();
        poller = new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler, 2, 2,
                Duration.ofSeconds(1), Duration.ZERO, observation);
        poller.start();
        callback.get().run();
        var order = inOrder(ledger, dispatcher);
        order.verify(ledger).grantReadyRuns(null, 2);
        order.verify(dispatcher).dispatch(run.actor(), run.query());
        order.verify(ledger).sampleQueuedBacklog();
        var meter = registry.get(AgentQueuedBacklogObservation.METER).gauge();
        assertEquals(0d, meter.value());
        assertTrue(meter.getId().getTags().isEmpty());
        callback.get().run();
        assertEquals(0d, meter.value());
        verify(ledger, times(1)).sampleQueuedBacklog();
        clock.set(Duration.ofSeconds(30).toNanos());
        callback.get().run();
        assertEquals(257d, meter.value());
        clock.set(Duration.ofSeconds(90).toNanos());
        assertTrue(Double.isNaN(meter.value()));
        verify(ledger, times(2)).sampleQueuedBacklog();
        poller.stop();
        callback.get().run();
        assertTrue(Double.isNaN(meter.value()));
        verify(ledger, times(2)).sampleQueuedBacklog();
    }

    @Test
    void backlog_probe_failure_is_unknown_and_does_not_block_the_next_dispatch() {
        var registry = new SimpleMeterRegistry();
        var clock = new AtomicLong();
        var observation = new AgentQueuedBacklogObservation(ledger, () -> registry, clock::get);
        var run = candidate(21, "00000000-0000-0000-0000-000000000121");
        when(ledger.grantReadyRuns(null, 2)).thenReturn(List.of(run));
        when(ledger.grantReadyRuns(run.cursor(), 2)).thenReturn(List.of());
        when(ledger.sampleQueuedBacklog()).thenThrow(new IllegalStateException("private backlog diagnostic")).thenReturn(6);
        poller.close();
        poller = new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler, 2, 2,
                Duration.ofSeconds(1), Duration.ZERO, observation);
        poller.start();
        callback.get().run();
        var meter = registry.get(AgentQueuedBacklogObservation.METER).gauge();
        assertTrue(Double.isNaN(meter.value()));
        clock.set(Duration.ofSeconds(30).toNanos());
        callback.get().run();
        assertEquals(6d, meter.value());
        when(ledger.grantReadyRuns(null, 2)).thenThrow(new IllegalStateException("private discovery diagnostic"));
        callback.get().run();
        assertTrue(Double.isNaN(meter.value()));
        verify(ledger, times(2)).sampleQueuedBacklog();
        verify(dispatcher).dispatch(run.actor(), run.query());
    }

    @Test
    void default_off_and_absent_registry_do_not_read_backlog() {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var stellar = (java.util.Map<?, ?>) yaml.getObject().get("stellar");
        var tianji = (java.util.Map<?, ?>) stellar.get("tianji");
        var agent = (java.util.Map<?, ?>) tianji.get("agent");
        var observationConfig = (java.util.Map<?, ?>) agent.get("run-observation");
        assertEquals(Boolean.FALSE, observationConfig.get("queued-backlog-metrics-enabled"));

        var flag = "stellar.tianji.agent.run-observation.queued-backlog-metrics-enabled";
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(TianjiGrantDispatchConfiguration.class)
                .withPropertyValues(flag + "=true")
                .run(context -> assertTrue(context.getBeansOfType(TianjiGrantDispatchPoller.class).isEmpty()));

        when(ledger.grantReadyRuns(null, 2)).thenReturn(List.of());
        poller.start();
        callback.get().run();
        new AgentQueuedBacklogObservation(ledger, () -> null, () -> 0).sampleIfDue();
        verify(ledger, never()).sampleQueuedBacklog();

        var registry = new SimpleMeterRegistry();
        var beans = new StaticListableBeanFactory();
        beans.addBean("meterRegistry", registry);
        var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("stellar.tianji.agent.execution-authorization.enabled", "true")
                .withProperty("stellar.tianji.agent.reconciliation.enabled", "true")
                .withProperty(flag, "true");
        var reconciler = new AgentRunReconciler(ledger, mock(TaskScheduler.class), new AgentReconciliationProperties());
        when(ledger.grantReadyRuns(null, 64)).thenReturn(List.of());
        when(ledger.sampleQueuedBacklog()).thenReturn(7);
        poller.close();
        poller = new TianjiGrantDispatchConfiguration().tianjiGrantDispatchPoller(
                ledger, dispatcher, authorizer, reconciler, scheduler, environment, beans.getBeanProvider(MeterRegistry.class));
        poller.start();
        callback.get().run();
        assertEquals(7d, registry.get(AgentQueuedBacklogObservation.METER).gauge().value());
        verify(ledger).sampleQueuedBacklog();
    }

    private static GrantReadyAgentRun candidate(long tenant, String runId) {
        var actor = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/tenants/" + tenant);
        return new GrantReadyAgentRun(actor, new GetAgentRunQuery(runId), Instant.parse("2026-10-29T00:00:00Z"));
    }
}
