package top.egon.cola.platform.tianji.agent.server.api;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/** Replays the database ledger and polls its watermark on the authenticated request thread. */
public final class AgentRunEventStream {
    private static final Logger log = LoggerFactory.getLogger(AgentRunEventStream.class);
    private static final int MAX_STREAMS = 32;
    private static final int MAX_PAGE = 8;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    private static final Duration MAX_CONNECTION = Duration.ofSeconds(30);
    private final AgentLedgerService ledger;
    private final TianjiAgentAccess access;
    private final Clock clock;
    private final long maxConnectionNanos;
    private final Semaphore capacity = new Semaphore(MAX_STREAMS);
    private final JsonMapper mapper = JsonMapper.builder().build();

    public AgentRunEventStream(AgentLedgerService ledger, TianjiAgentAccess access, Clock clock) {
        this(ledger, access, clock, MAX_CONNECTION);
    }

    AgentRunEventStream(AgentLedgerService ledger, TianjiAgentAccess access, Clock clock, Duration maxConnection) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.access = Objects.requireNonNull(access, "access");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxConnection == null || maxConnection.isNegative() || maxConnection.isZero()
                || maxConnection.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("stream connection duration is invalid");
        }
        this.maxConnectionNanos = maxConnection.toNanos();
    }

    public void stream(TianjiAgentAccess.Authorized initial, AgentEventsQuery requested,
                       HttpServletResponse response) throws IOException {
        int pageSize = Math.min(requested.limit(), MAX_PAGE);
        if (!capacity.tryAcquire()) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "event stream capacity reached");
        }
        try {
            AgentRunDTO run = ledger.get(initial.actor(), new GetAgentRunQuery(requested.runId()));
            if (requested.afterSequence() > run.lastEventSequence()) {
                throw new IllegalArgumentException("event cursor is beyond the run watermark");
            }
            List<AgentEventDTO> page = ledger.events(initial.actor(),
                    new AgentEventsQuery(requested.runId(), requested.afterSequence(), pageSize));
            validatePage(requested.afterSequence(), run, page);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("text/event-stream");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Accel-Buffering", "no");
            pump(response, initial, requested, pageSize);
        } finally {
            capacity.release();
        }
    }

    private void pump(HttpServletResponse response, TianjiAgentAccess.Authorized initial,
                      AgentEventsQuery requested, int pageSize) throws IOException {
        long cursor = requested.afterSequence();
        long started = System.nanoTime();
        Instant nextHeartbeat = clock.instant();
        try {
            while (System.nanoTime() - started < maxConnectionNanos) {
                TianjiAgentAccess.Authorized current = access.require(TianjiAgentPermissions.EVENTS_API);
                if (!current.actor().equals(initial.actor()) || !current.expiresAt().equals(initial.expiresAt())) {
                    throw new IllegalStateException("stream identity changed");
                }
                if (!clock.instant().isBefore(current.expiresAt())) return;
                AgentRunDTO run = ledger.get(current.actor(), new GetAgentRunQuery(requested.runId()));
                List<AgentEventDTO> page = ledger.events(current.actor(),
                        new AgentEventsQuery(requested.runId(), cursor, pageSize));
                validatePage(cursor, run, page);
                for (AgentEventDTO event : page) {
                    if (!clock.instant().isBefore(current.expiresAt())) return;
                    write(response, "id: " + event.sequence() + "\nevent: agent.event\ndata: "
                            + mapper.writeValueAsString(event) + "\n\n");
                    cursor = event.sequence();
                }
                if (AgentRunTransitions.terminal(run.status()) && cursor == run.lastEventSequence()) {
                    write(response, "event: stream.complete\n\n");
                    return;
                }
                if (page.isEmpty()) {
                    if (!clock.instant().isBefore(nextHeartbeat)) {
                        write(response, ": heartbeat\n\n");
                        nextHeartbeat = clock.instant().plus(HEARTBEAT_INTERVAL);
                    }
                    long remaining = maxConnectionNanos - (System.nanoTime() - started);
                    if (remaining > 0) Thread.sleep(Duration.ofNanos(Math.min(POLL_INTERVAL.toNanos(), remaining)));
                }
            }
            write(response, "event: stream.reconnect\n\n");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            if (!response.isCommitted()) throw failure;
            if (failure instanceof AccessDeniedException || failure instanceof AuthenticationException) {
                log.debug("Agent event stream authorization ended: {}", failure.getClass().getSimpleName());
            } else {
                log.warn("Agent event stream stopped after a polling failure: {}", failure.getClass().getSimpleName());
            }
        }
    }

    private static void write(HttpServletResponse response, String frame) throws IOException {
        response.getOutputStream().write(frame.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private static void validatePage(long cursor, AgentRunDTO run, List<AgentEventDTO> events) {
        if ((!events.isEmpty() && events.getFirst().sequence() != cursor + 1)
                || (events.isEmpty() && cursor < run.lastEventSequence())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_EVENT_CURSOR_EXPIRED, "event cursor is unavailable");
        }
        long expected = cursor;
        for (AgentEventDTO event : events) {
            if (event.sequence() != ++expected) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_EVENT_CURSOR_EXPIRED, "event cursor is unavailable");
            }
        }
    }
}
