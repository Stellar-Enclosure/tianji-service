package top.egon.cola.platform.tianji.agent.server.security;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumePermit;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeRequest;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient.Expectation;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Explicit host-owned RESUME adapter for separately issued opaque grants and current Jianshen decisions. */
public final class TianjiCheckpointResumeGrantAuthorizer implements ResumeAuthorization, AutoCloseable {
    private static final String INTENT_DOMAIN = "TIANJI_AGENT_RUN_RESUME_INTENT_V1";

    private final URI resource;
    private final String systemCode;
    private final TrustedIssuerRegistry issuers;
    private final Map<String, BoundClient> clients;
    private final ResumeTicketSource tickets;
    private final Duration timeout;
    private final Clock clock;
    private final ThreadPoolExecutor executor;

    public TianjiCheckpointResumeGrantAuthorizer(URI resource, String systemCode, TrustedIssuerRegistry issuers,
            Map<URI, HttpExecutionAuthorizationClient> clients, ResumeTicketSource tickets, Duration timeout, int concurrency) {
        this(resource, systemCode, issuers, clients, tickets, timeout, concurrency, Clock.systemUTC());
    }

    TianjiCheckpointResumeGrantAuthorizer(URI resource, String systemCode, TrustedIssuerRegistry issuers,
            Map<URI, HttpExecutionAuthorizationClient> clients, ResumeTicketSource tickets, Duration timeout, int concurrency,
            Clock clock) {
        this.resource = TianjiExecutionAuthorityBindings.resource(resource);
        this.systemCode = TianjiExecutionGrantAuthorizer.systemCode(systemCode);
        this.issuers = Objects.requireNonNull(issuers, "issuers");
        this.tickets = Objects.requireNonNull(tickets, "tickets");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (clients == null || clients.isEmpty() || clients.size() > 4096
                || timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0
                || timeout.compareTo(Duration.ofSeconds(30)) > 0 || concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("TIANJI_CHECKPOINT_RESUME_AUTHORITY_CONFIGURATION_INVALID");
        }
        this.timeout = timeout;
        var exactClients = new HashMap<String, BoundClient>();
        clients.forEach((issuer, client) -> {
            URI exact = TianjiExecutionAuthorityBindings.resource(issuer);
            String hash = TrustedIssuerRegistry.issuerHash(exact);
            if (client == null || exactClients.putIfAbsent(hash, new BoundClient(exact, client)) != null) {
                throw new IllegalArgumentException("TIANJI_CHECKPOINT_RESUME_AUTHORITY_BINDING_INVALID");
            }
        });
        this.clients = Map.copyOf(exactClients);
        executor = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-resume-authority-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    boolean usesTicketSource(ResumeTicketSource candidate) {
        return tickets == candidate;
    }

    boolean usesResource(URI candidate) {
        return candidate != null && resource.toASCIIString().equals(candidate.toASCIIString());
    }

    /** Derives the exact binding for both issuance and current decision without a USER bearer. */
    public static Expectation expectation(ResumeRequest request, URI issuer, URI resource) {
        Objects.requireNonNull(request, "request");
        URI exactIssuer = TianjiExecutionAuthorityBindings.resource(issuer);
        URI exactResource = TianjiExecutionAuthorityBindings.resource(resource);
        var execution = request.execution();
        var actor = execution.actor();
        if (!execution.sessionRequired() || actor.subjectType() != TrustedAgentActor.SubjectType.USER
                || !TrustedIssuerRegistry.issuerHash(exactIssuer).equals(actor.issuerFingerprint())) {
            throw new IllegalArgumentException("TIANJI_CHECKPOINT_RESUME_AUTHORITY_BINDING_INVALID");
        }
        String binding = request.bindingSha256(exactResource);
        // The owner issuer's deterministic grant ID does not include the subject or binding digest.
        // A v8 UUID from the full binding isolates commands, cuts and verified owners before issuance.
        byte[] digest = HexFormat.of().parseHex(OrchestrationRequestDigests.hash(List.of(INTENT_DOMAIN, binding), Map.of()));
        digest[6] = (byte) ((digest[6] & 0x0f) | 0x80);
        digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
        var bytes = ByteBuffer.wrap(digest);
        var intent = new UUID(bytes.getLong(), bytes.getLong()).toString();
        return new Expectation(exactIssuer, Long.toString(actor.tenantId()), actor.subjectId(), exactResource,
                AgentCheckpointTakeoverService.PURPOSE, intent, binding, true, execution.deadlineAt());
    }

    @Override
    public Optional<ResumePermit> authorize(ResumeRequest request) {
        if (request == null || executor.isShutdown() || Thread.currentThread().isInterrupted()
                || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) throw unavailable();
        var execution = request.execution();
        var actor = execution.actor();
        if (!execution.sessionRequired() || actor.subjectType() != TrustedAgentActor.SubjectType.USER
                || "LEGACY".equals(actor.issuerFingerprint())) throw unavailable();
        var client = clients.get(actor.issuerFingerprint());
        if (client == null) throw unavailable();
        Duration remaining = Duration.between(clock.instant(), execution.deadlineAt());
        if (remaining.isNegative() || remaining.isZero()) throw unavailable();
        Duration window = remaining.compareTo(timeout) < 0 ? remaining : timeout;
        Future<Optional<ResumePermit>> pending;
        try {
            var trace = TraceContext.capture();
            pending = executor.submit(() -> {
                try (var ignored = trace.open()) {
                    return decide(request, client);
                }
            });
        } catch (RejectedExecutionException refused) {
            throw unavailable();
        }
        try {
            var result = pending.get(window.toNanos(), TimeUnit.NANOSECONDS);
            var now = clock.instant();
            if (executor.isShutdown() || !execution.deadlineAt().isAfter(now)
                    || result.isPresent() && !result.get().expiresAt().isAfter(now)) throw unavailable();
            return result;
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (TimeoutException expired) {
            pending.cancel(true);
            throw unavailable();
        } catch (ExecutionException failed) {
            throw unavailable();
        }
    }

    private Optional<ResumePermit> decide(ResumeRequest request, BoundClient client) {
        var expected = expectation(request, client.issuer(), resource);
        var trust = issuers.findByIssuer(client.issuer().toASCIIString()).orElseThrow(TianjiCheckpointResumeGrantAuthorizer::unavailable);
        if (trust.realmType() != TrustedIssuerRegistry.RealmType.TENANT || !expected.tenantId().equals(trust.tenantId())) throw unavailable();
        var current = tickets.current(expected).orElseThrow(TianjiCheckpointResumeGrantAuthorizer::unavailable);
        if (!expected.equals(current.binding()) || !current.ticket().expiresAt().isAfter(clock.instant())
                || Thread.currentThread().isInterrupted()) throw unavailable();
        var ticket = current.ticket();
        ExecutionAuthorizationContract.Decision decision;
        try {
            decision = client.http().decide(new ExecutionAuthorizationContract.Request(systemCode, ticket.grantId(), ticket.ciphertext()), expected);
        } catch (HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException denied) {
            requireCurrent(expected, current, trust);
            return Optional.empty();
        }
        requireCurrent(expected, current, trust);
        // The client already verifies the full expectation and grant ID. Also bound the answer by the issued envelope's known expiry.
        if (decision == null || decision.expiresAt().isAfter(ticket.expiresAt()) || !decision.expiresAt().isAfter(clock.instant())) {
            throw unavailable();
        }
        return Optional.of(new ResumePermit(request, decision.expiresAt()));
    }

    private void requireCurrent(Expectation expected, BoundResumeTicket ticket, TrustedIssuerRegistry.TrustedIssuerVO trust) {
        if (Thread.currentThread().isInterrupted() || executor.isShutdown()
                || issuers.findByIssuer(expected.issuer().toASCIIString()).filter(trust::equals).isEmpty()
                || tickets.current(expected).filter(ticket::equals).isEmpty()
                || !ticket.ticket().expiresAt().isAfter(clock.instant())) throw unavailable();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    /** The host supplies a current opaque ticket by exact binding. No ordinary Run store or USER bearer is read. */
    @FunctionalInterface
    public interface ResumeTicketSource {
        Optional<BoundResumeTicket> current(Expectation expected);
    }

    public record BoundResumeTicket(Expectation binding, AgentExecutionGrant.Ticket ticket) {
        public BoundResumeTicket {
            if (binding == null || ticket == null || !AgentCheckpointTakeoverService.PURPOSE.equals(binding.purpose())
                    || !binding.sessionRequired() || ticket.expiresAt().isAfter(binding.deadlineAt())) {
                throw new IllegalArgumentException("TIANJI_CHECKPOINT_RESUME_TICKET_INVALID");
            }
        }

        @Override
        public String toString() {
            return "BoundResumeTicket[redacted]";
        }
    }

    private record BoundClient(URI issuer, HttpExecutionAuthorizationClient http) { }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent checkpoint resume authorization is unavailable");
    }
}
