package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentRunEventStreamCapacityTest {
    @Test
    void idle_connection_announces_rotation_without_completing_the_run() throws Exception {
        Instant now = Instant.now();
        var actor = new TrustedAgentActor(20, TrustedAgentActor.SubjectType.SERVICE, "worker");
        var authorized = new TianjiAgentAccess.Authorized(actor, now.plusSeconds(120));
        var access = mock(TianjiAgentAccess.class);
        when(access.require(anyString())).thenReturn(authorized);
        var ledger = mock(AgentLedgerService.class);
        when(ledger.get(eq(actor), any())).thenReturn(new AgentRunDTO("run-1", "research", 1, null,
                AgentRunStatusEnum.RUNNING, null, false, 1, 2, 1, now.plusSeconds(60), now, null));
        when(ledger.events(eq(actor), any())).thenReturn(List.of());
        var stream = new AgentRunEventStream(ledger, access, Clock.systemUTC(), Duration.ofMillis(25));
        var response = new MockHttpServletResponse();
        stream.stream(authorized, new AgentEventsQuery("run-1", 1, 10), response);
        assertTrue(response.getContentAsString().contains("event: stream.reconnect\n\n"));
        assertFalse(response.getContentAsString().contains("stream.complete"));
    }

    @Test
    void blocked_consumers_cannot_use_more_than_the_bounded_stream_capacity() throws Exception {
        Instant now = Instant.now();
        var actor = new TrustedAgentActor(20, TrustedAgentActor.SubjectType.SERVICE, "worker");
        var authorized = new TianjiAgentAccess.Authorized(actor, now.plusSeconds(120));
        var access = mock(TianjiAgentAccess.class);
        when(access.require(anyString())).thenReturn(authorized);
        var ledger = mock(AgentLedgerService.class);
        var run = new AgentRunDTO("run-1", "research", 1, null, AgentRunStatusEnum.COMPLETED,
                null, false, 1, 2, 1, now.plusSeconds(60), now, now);
        var event = new AgentEventDTO("event-1", "run-1", 1, "RUN_COMPLETED", null, null, null,
                now, null, "1", Map.of());
        when(ledger.get(eq(actor), any())).thenReturn(run);
        when(ledger.events(eq(actor), any())).thenReturn(List.of(event));
        var stream = new AgentRunEventStream(ledger, access, Clock.systemUTC());
        var query = new AgentEventsQuery("run-1", 0, 1);
        var entered = new CountDownLatch(32);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                tasks.add(workers.submit(() -> {
                    stream.stream(authorized, query, new BlockingResponse(entered, release));
                    return null;
                }));
            }
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                AgentLedgerException full = assertThrows(AgentLedgerException.class,
                        () -> stream.stream(authorized, query, new MockHttpServletResponse()));
                assertEquals(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, full.code());
            } finally {
                release.countDown();
            }
            for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
        }
    }

    private static final class BlockingResponse extends MockHttpServletResponse {
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private BlockingResponse(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public void flushBuffer() {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test consumer did not release");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test consumer interrupted", interrupted);
            }
            super.flushBuffer();
        }
    }
}
