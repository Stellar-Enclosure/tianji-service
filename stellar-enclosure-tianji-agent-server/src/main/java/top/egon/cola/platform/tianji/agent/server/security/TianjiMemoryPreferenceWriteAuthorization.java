package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.common.trace.TraceContext;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceCommands;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceWritePermit;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionScopeAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Current independent WRITE authority, translated into a one-use capability for one canonical candidate command. */
public final class TianjiMemoryPreferenceWriteAuthorization implements MemoryAuthorization, AutoCloseable {
    private final AgentLedgerService ledger;
    private final MemoryPreferenceIntentStore store;
    private final List<MemoryPreferenceSourceBinding> bindings;
    private final Map<String, MemoryExtractionPolicy> policies;
    private final URI resource;
    private final String systemCode;
    private final TrustedIssuerRegistry issuers;
    private final Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients;
    private final Duration timeout;
    private final Clock clock;
    private final ThreadPoolExecutor calls;
    private final MemoryPreferenceExtraction extraction = new MemoryPreferenceExtraction();

    public TianjiMemoryPreferenceWriteAuthorization(AgentLedgerService ledger, MemoryPreferenceIntentStore store, List<MemoryPreferenceSourceBinding> bindings,
            List<MemoryExtractionPolicy> policies, IdpStarterProperties starter, IdentityAuthorityProperties authority, TrustedIssuerRegistry issuers,
            IdpServiceOAuth2Client oauth, ObjectMapper mapper, Rbac3StarterProperties rbac, Duration timeout, int concurrency) {
        this(ledger, store, bindings, policies, TianjiExecutionAuthorityBindings.resource(starter.getResourceUri()), rbac.getSystemCode(), issuers,
                TianjiExecutionGrantAuthorizer.clients(starter, authority, issuers, oauth, mapper, rbac,
                        IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE, ExecutionScopeAuthorizationContract.SERVICE_SCOPE), timeout, concurrency, Clock.systemUTC());
    }

    TianjiMemoryPreferenceWriteAuthorization(AgentLedgerService ledger, MemoryPreferenceIntentStore store, List<MemoryPreferenceSourceBinding> bindings,
            List<MemoryExtractionPolicy> policies, URI resource, String systemCode, TrustedIssuerRegistry issuers,
            Map<String, TianjiExecutionGrantAuthorizer.BoundClient> clients, Duration timeout, int concurrency, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.store = Objects.requireNonNull(store, "store");
        this.resource = TianjiExecutionAuthorityBindings.resource(resource); this.systemCode = TianjiExecutionGrantAuthorizer.systemCode(systemCode);
        this.issuers = Objects.requireNonNull(issuers, "issuers"); this.clients = Map.copyOf(clients);
        this.clock = Objects.requireNonNull(clock, "clock"); this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (!store.usesLedger(ledger) || bindings == null || bindings.isEmpty() || bindings.size() > 1000 || policies == null || policies.isEmpty() || policies.size() > 32
                || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0 || concurrency < 1 || concurrency > 8) throw invalid();
        this.bindings = List.copyOf(bindings); var configured = new HashMap<String, MemoryExtractionPolicy>(); var versions = new HashSet<String>();
        for (var policy : policies) if (!versions.add(policy.key() + '@' + policy.version()) || configured.putIfAbsent(policy.reference(), policy) != null) throw invalid();
        this.policies = Map.copyOf(configured); var ids = new HashSet<String>(); var sources = new HashSet<List<String>>(); var definitions = new HashMap<List<String>, String>();
        for (var binding : this.bindings) {
            var client = this.clients.get(binding.issuerFingerprint());
            var definition = List.of(Long.toString(binding.tenantId()), binding.issuerFingerprint(), binding.definitionKey(), Long.toString(binding.definitionVersion()));
            String previous = definitions.putIfAbsent(definition, binding.definitionSha256());
            var source = new java.util.ArrayList<>(definition); source.add(binding.scopeId());
            if (!ids.add(binding.id()) || !sources.add(List.copyOf(source)) || previous != null && !previous.equals(binding.definitionSha256()) || !configured.containsKey(binding.policyRef())
                    || client == null || !client.tenantId().equals(Long.toString(binding.tenantId())) || !TrustedIssuerRegistry.issuerHash(client.issuer()).equals(binding.issuerFingerprint())) throw invalid();
        }
        calls = new ThreadPoolExecutor(0, concurrency, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofVirtual().inheritInheritableThreadLocals(false).name("tianji-memory-write-authorization-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean usesBindings(List<MemoryPreferenceSourceBinding> configured, List<MemoryExtractionPolicy> configuredPolicies) {
        return bindings.equals(configured) && policies.equals(configuredPolicies.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(MemoryExtractionPolicy::reference, value -> value)));
    }
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate && store.usesLedger(candidate); }
    boolean usesIssuerRegistry(TrustedIssuerRegistry candidate) { return issuers == candidate; }
    boolean usesResource(URI candidate) { return resource.toASCIIString().equals(candidate.toASCIIString()); }

    public Prepared prepare(MemoryPreferenceIntent.Lease lease, Instant deadline) { return current(lease, deadline).prepared(); }

    /** No caller-supplied body is trusted: the current source is re-authorized and the complete next command is recomputed. */
    public MemoryOperationContext permit(MemoryPreferenceIntent.Lease lease, UpsertMemoryCommand command, Instant deadline) {
        var current = current(lease, deadline); var commands = current.prepared().commands();
        if (lease.totalCount() == null || lease.totalCount() != commands.size() || lease.nextIndex() >= commands.size()
                || !PostgresMemoryService.preferenceCommandListSha256(commands).equals(lease.commandsSha256()) || !commands.get(lease.nextIndex()).equals(command)) throw denied();
        if (calls.isShutdown() || Thread.currentThread().isInterrupted() || !clock.instant().isBefore(current.expiresAt()) || current.monotonicDeadline() - System.nanoTime() <= 0) throw unavailable();
        var permit = new Permit(lease, command, current.expiresAt(), current.monotonicDeadline(), current.trust());
        return new MemoryOperationContext(lease.evidence().reservation().run().actor(), current.expiresAt(), null, permit);
    }

    @Override public Decision authorize(Request request) {
        if (request == null || !(request.context().preferenceWrite() instanceof Permit permit) || permit.authority() != this) return Decision.DENY;
        if (calls.isShutdown() || Thread.currentThread().isInterrupted() || TransactionSynchronizationManager.isActualTransactionActive() || !permit.live()) return Decision.UNAVAILABLE;
        var command = permit.command();
        if (request.context().runAuthorization() != null || !request.context().actor().equals(permit.actor()) || request.context().deadlineAt().isAfter(permit.expiresAt())
                || !clock.instant().isBefore(request.context().deadlineAt()) || request.operation() != Operation.UPSERT || !request.owner().equals(command.owner())
                || !request.scopeId().equals(command.scopeId()) || !Objects.equals(request.memoryId(), command.memoryId()) || request.expectedRevision() != 0
                || !Objects.equals(request.sourceRunId(), command.source().sourceRunId()) || !Objects.equals(request.sourceEventId(), command.source().sourceEventId())) return Decision.DENY;
        return permit.consumed.compareAndSet(false, true) ? Decision.ALLOW : Decision.DENY;
    }

    private Current current(MemoryPreferenceIntent.Lease lease, Instant deadline) {
        Objects.requireNonNull(lease, "lease"); Objects.requireNonNull(deadline, "deadline");
        if (calls.isShutdown() || Thread.currentThread().isInterrupted() || TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        Instant maximum = earliest(deadline, lease.expiresAt(), lease.evidence().ticket().expiresAt(), lease.evidence().reservation().deadlineAt(), clock.instant().plus(timeout));
        long remaining = Duration.between(clock.instant(), maximum).toNanos(); if (remaining <= 0) throw unavailable(); long monotonic = System.nanoTime() + remaining;
        try {
            var trace = TraceContext.capture(); var future = calls.submit(() -> { try (var ignored = trace.open()) { return evaluate(lease, maximum, monotonic); } });
            try { return future.get(remaining, TimeUnit.NANOSECONDS); }
            catch (InterruptedException interrupted) { future.cancel(true); Thread.currentThread().interrupt(); throw unavailable(); }
            catch (TimeoutException expired) { future.cancel(true); throw unavailable(); }
            catch (ExecutionException failure) { if (failure.getCause() instanceof AgentLedgerException known) throw known; throw unavailable(); }
        } catch (RejectedExecutionException full) { throw unavailable(); }
    }

    private Current evaluate(MemoryPreferenceIntent.Lease lease, Instant deadline, long monotonic) {
        var reservation = lease.evidence().reservation(); var run = reservation.run(); var binding = reservation.binding();
        if (!bindings.contains(binding) || !binding.matches(run) || !resource.toASCIIString().equals(reservation.resource().toASCIIString())) throw denied();
        var client = clients.get(run.actor().issuerFingerprint());
        var trust = client == null ? null : issuers.findByIssuer(client.issuer().toASCIIString()).orElse(null);
        if (trust == null || trust.status() != TrustedIssuerRegistry.Status.ACTIVE || trust.realmType() != TrustedIssuerRegistry.RealmType.TENANT
                || !Long.toString(run.actor().tenantId()).equals(trust.tenantId())) throw unavailable();
        check(lease, deadline, monotonic);
        var expected = new HttpExecutionAuthorizationClient.Expectation(client.issuer(), client.tenantId(), run.actor().subjectId(), resource, MemoryPreferenceIntent.PURPOSE,
                reservation.intentId().toString(), reservation.bindingSha256(), false, reservation.deadlineAt());
        ExecutionScopeAuthorizationContract.Decision decision;
        try { decision = client.client().decideScopes(new ExecutionAuthorizationContract.Request(systemCode, lease.evidence().ticket().grantId(), lease.evidence().ticket().ciphertext()), expected); }
        catch (HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException rejected) { unchanged(lease, client, trust, deadline, monotonic); throw denied(); }
        unchanged(lease, client, trust, deadline, monotonic);
        Instant expires = earliest(deadline, decision.execution().expiresAt());
        if (decision.generatedAt().isAfter(clock.instant()) || !expires.isAfter(ledger.databaseNow()) || decision.execution().expiresAt().isAfter(lease.evidence().ticket().expiresAt())) throw unavailable();
        boolean allowed = true;
        for (var permission : List.of(TianjiAgentPermissions.MEMORY_WRITE, TianjiAgentPermissions.READ)) {
            var scope = decision.dataScopes().get(permission);
            if (scope != null && scope.decision() == top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.INDETERMINATE) throw unavailable();
            allowed &= scope != null && scope.decision() == top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision.ALLOW;
        }
        if (!allowed) throw denied();
        var material = ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId()));
        if (!run.equals(material.authorization()) || !run.runId().equals(material.run().runId()) || material.run().status() != AgentRunStatusEnum.COMPLETED
                || material.run().cancelRequested() || material.run().finishedAt() == null || material.run().lastEventSequence() < 1 || material.snapshot().processLink() != null) throw denied();
        var events = ledger.events(run.actor(), new AgentEventsQuery(run.runId(), material.run().lastEventSequence() - 1, 1));
        if (events.size() != 1 || !lease.sourceEventId().equals(events.getFirst().eventId()) || !run.runId().equals(events.getFirst().runId())
                || events.getFirst().sequence() != material.run().lastEventSequence() || !"RUN_STATUS_CHANGED".equals(events.getFirst().type()) || !"COMPLETED".equals(events.getFirst().payload().get("status"))) throw denied();
        var policy = policies.get(binding.policyRef());
        var extracted = extraction.evaluate(new MemoryPreferenceExtraction.Source(material.run().status(), MemoryPreferenceExtraction.SourceKind.USER_INPUT, true,
                material.snapshot().input().text() == null ? "" : material.snapshot().input().text()), policy);
        var command = new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, run.actor().subjectId()), binding.scopeId(), run.runId(), lease.sourceEventId(), binding.id(), binding.policyRef());
        var prepared = new Prepared(extracted.outcome(), MemoryPreferenceCommands.create(run.actor(), command, policy, extracted));
        unchanged(lease, client, trust, expires, monotonic);
        return new Current(prepared, expires, monotonic, trust);
    }

    private void unchanged(MemoryPreferenceIntent.Lease lease, TianjiExecutionGrantAuthorizer.BoundClient client, TrustedIssuerRegistry.TrustedIssuerVO trust, Instant deadline, long monotonic) {
        if (!issuers.findByIssuer(client.issuer().toASCIIString()).filter(trust::equals).isPresent()) throw unavailable(); check(lease, deadline, monotonic);
    }
    private void check(MemoryPreferenceIntent.Lease lease, Instant deadline, long monotonic) {
        if (calls.isShutdown() || Thread.currentThread().isInterrupted() || !clock.instant().isBefore(deadline) || monotonic - System.nanoTime() <= 0) throw unavailable();
        if (!lease.equals(store.currentLease(lease))) throw unavailable();
        if (!clock.instant().isBefore(deadline) || monotonic - System.nanoTime() <= 0) throw unavailable();
    }
    private static Instant earliest(Instant... values) { return java.util.Arrays.stream(values).min(Instant::compareTo).orElseThrow(); }

    public record Prepared(MemoryPreferenceExtraction.Outcome outcome, List<UpsertMemoryCommand> commands) {
        public Prepared { Objects.requireNonNull(outcome, "outcome"); commands = List.copyOf(commands); if (commands.size() > 32 || (outcome == MemoryPreferenceExtraction.Outcome.ELIGIBLE) != !commands.isEmpty()) throw invalid(); }
        @Override public String toString() { return "MemoryPreferencePrepared[redacted]"; }
    }
    private record Current(Prepared prepared, Instant expiresAt, long monotonicDeadline, TrustedIssuerRegistry.TrustedIssuerVO trust) { @Override public String toString() { return "MemoryPreferenceCurrent[redacted]"; } }

    private final class Permit implements MemoryPreferenceWritePermit {
        private final MemoryPreferenceIntent.Lease lease;
        private final UpsertMemoryCommand command;
        private final Instant expiresAt;
        private final long monotonicDeadline;
        private final TrustedIssuerRegistry.TrustedIssuerVO trust;
        private final AtomicBoolean consumed = new AtomicBoolean();
        private final AtomicBoolean fenced = new AtomicBoolean();
        private Permit(MemoryPreferenceIntent.Lease lease, UpsertMemoryCommand command, Instant expiresAt, long monotonicDeadline, TrustedIssuerRegistry.TrustedIssuerVO trust) {
            this.lease = lease; this.command = command; this.expiresAt = expiresAt; this.monotonicDeadline = monotonicDeadline; this.trust = trust;
        }
        private TianjiMemoryPreferenceWriteAuthorization authority() { return TianjiMemoryPreferenceWriteAuthorization.this; }
        private boolean live() {
            return !calls.isShutdown() && clock.instant().isBefore(expiresAt) && monotonicDeadline - System.nanoTime() > 0
                    && issuers.findByIssuer(trust.issuer().toASCIIString()).filter(trust::equals).isPresent();
        }
        @Override public TrustedAgentActor actor() { return lease.evidence().reservation().run().actor(); }
        @Override public UpsertMemoryCommand command() { return command; }
        @Override public Instant expiresAt() { return expiresAt; }
        @Override public boolean usesDataSource(DataSource candidate) { return store.usesDataSource(candidate); }
        @Override public void verifyTransaction() {
            if (!live() || !consumed.get() || !fenced.compareAndSet(false, true)) throw unavailable();
            store.verifyFence(lease); if (!live()) throw unavailable();
        }
        @Override public String toString() { return "MemoryPreferenceWritePermit[redacted]"; }
    }
    @Override public void close() { calls.shutdownNow(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory preference write authority binding is invalid"); }
    private static AgentLedgerException denied() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current Memory preference write or source access was denied"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory preference write authority is unavailable"); }
}
