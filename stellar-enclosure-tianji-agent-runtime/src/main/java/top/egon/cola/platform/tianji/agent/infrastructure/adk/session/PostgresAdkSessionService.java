package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionException;
import com.google.adk.sessions.SessionNotFoundException;
import com.google.adk.sessions.State;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/** ADK's native session SPI, bound to an authenticated tenant/subject by trusted host code. */
public final class PostgresAdkSessionService implements BaseSessionService {
    private static final int MAX_SESSIONS = 500;
    private static final int MAX_EVENTS = 1_000;
    private static final long MAX_HISTORY_BYTES = 8L * 1_048_576;
    private static final int MAX_SCOPE_ENTRIES = 500;
    private static final long MAX_SCOPE_BYTES = 4L * 1_048_576;
    private static final Duration MIN_LEASE = Duration.ofSeconds(1);
    private static final Duration MAX_LEASE = Duration.ofMinutes(5);
    private final TrustedAgentActor actor;
    private final String appName;
    private final String userId;
    private final boolean allowAppStateWrites;
    private final JdbcAdkSessionRepository repository;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final AdkSessionEventCodec eventCodec;
    private final AdkSessionStateCodec stateCodec;
    private final Map<Session, Long> revisions;
    private final SessionLease lease;

    /** App-wide state writes are disabled unless the trusted host explicitly enables them. */
    public PostgresAdkSessionService(DataSource dataSource, TrustedAgentActor actor, String appName) {
        this(dataSource, actor, appName, false);
    }

    public PostgresAdkSessionService(DataSource dataSource, TrustedAgentActor actor, String appName,
                                     boolean allowAppStateWrites) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.actor = Objects.requireNonNull(actor, "actor");
        if (!validApp(appName) || actor.subjectId().indexOf('\0') >= 0) throw invalid();
        this.appName = appName;
        this.userId = actor.subjectType().name() + ":" + actor.subjectId();
        this.allowAppStateWrites = allowAppStateWrites;
        this.repository = new JdbcAdkSessionRepository(new JdbcTemplate(dataSource), actor, appName);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        this.write = transaction(manager, false);
        this.read = transaction(manager, true);
        this.eventCodec = new AdkSessionEventCodec();
        this.stateCodec = new AdkSessionStateCodec();
        this.revisions = Collections.synchronizedMap(new WeakHashMap<>());
        this.lease = null;
    }

    private PostgresAdkSessionService(PostgresAdkSessionService source, SessionLease lease) {
        this.actor = source.actor;
        this.appName = source.appName;
        this.userId = source.userId;
        this.allowAppStateWrites = source.allowAppStateWrites;
        this.repository = source.repository;
        this.write = source.write;
        this.read = source.read;
        this.eventCodec = source.eventCodec;
        this.stateCodec = source.stateCodec;
        this.revisions = source.revisions;
        this.lease = lease;
    }

    /** The ADK user identifier is a local namespace; the database additionally binds tenant and principal type. */
    public String adkUserId() { return userId; }

    /** Acquires a database-timed session lease for a live run that references this session. */
    public SessionLease acquireSessionLease(String sessionId, AgentRunLease runLease, Duration duration) {
        String id = sessionId(sessionId);
        long millis = leaseMillis(duration);
        Objects.requireNonNull(runLease, "runLease");
        String owner = UUID.randomUUID().toString();
        return write.execute(status -> {
            requireRunFence(id, runLease);
            JdbcAdkSessionRepository.LeaseRow row = repository.acquire(id, owner, millis).orElseGet(() -> {
                if (repository.session(id, false).isEmpty()) throw notFound();
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, "ADK session is already in use");
            });
            if (!repository.liveFences(id, runLease, owner, row.epoch())) throw leaseExpired();
            return new SessionLease(actor, appName, id, owner, row.epoch(), row.expiresAt(), runLease);
        });
    }

    public SessionLease renewSessionLease(SessionLease existing, Duration duration) {
        requireLeaseScope(existing);
        long millis = leaseMillis(duration);
        return write.execute(status -> {
            requireRunFence(existing.sessionId, existing.runLease);
            JdbcAdkSessionRepository.LeaseRow row = repository.renew(existing.sessionId, existing.owner,
                    existing.epoch, millis).orElseThrow(PostgresAdkSessionService::leaseExpired);
            if (!repository.liveFences(existing.sessionId, existing.runLease, existing.owner, existing.epoch)) throw leaseExpired();
            return new SessionLease(actor, appName, existing.sessionId, existing.owner, existing.epoch,
                    row.expiresAt(), existing.runLease);
        });
    }

    /** Releases only this lease epoch, including after the run has stopped. */
    public boolean releaseSessionLease(SessionLease existing) {
        requireLeaseScope(existing);
        return Boolean.TRUE.equals(write.execute(status -> repository.release(existing.sessionId, existing.owner, existing.epoch)));
    }

    /** Returns a run-bound adapter; every durable append checks both database leases. */
    public PostgresAdkSessionService withLease(SessionLease existing) {
        requireLeaseScope(existing);
        return new PostgresAdkSessionService(this, existing);
    }

    @Override
    @Deprecated
    public Single<Session> createSession(String appName, String userId, ConcurrentMap<String, Object> state,
                                         String requestedId) {
        return createSession(appName, userId, (Map<String, Object>) state, requestedId);
    }

    @Override
    public Single<Session> createSession(String requestedApp, String requestedUser, Map<String, Object> initialState,
                                         String requestedId) {
        return Single.fromCallable(() -> {
            requireIdentity(requestedApp, requestedUser);
            if (lease != null) throw leaseExpired();
            String id = requestedId == null || requestedId.isBlank() ? UUID.randomUUID().toString()
                    : sessionId(requestedId.trim());
            Map<String, Object> delta = initialState == null ? Map.of() : new HashMap<>(initialState);
            SessionView view = write.execute(status -> {
                Map<String, Object> local = new HashMap<>();
                // Prepare local state before inserting, then apply scoped changes in the same transaction.
                applyLocalState(local, delta);
                if (repository.insertSession(id, stateCodec.encode(local)) != 1) {
                    throw new SessionException(SessionException.SESSION_ALREADY_EXISTS);
                }
                repository.insertLease(id);
                applyScopedState(delta);
                return view(repository.session(id, false).orElseThrow(), List.of());
            });
            return register(view);
        });
    }

    @Override
    public Maybe<Session> getSession(String requestedApp, String requestedUser, String sessionId,
                                     Optional<GetSessionConfig> config) {
        return Maybe.defer(() -> {
            requireIdentity(requestedApp, requestedUser);
            String id = sessionId(sessionId);
            Objects.requireNonNull(config, "config");
            SessionView view = readableSession(id, row -> view(row, loadEvents(row, config)));
            return view == null ? Maybe.empty() : Maybe.just(register(view));
        });
    }

    @Override
    public Single<ListSessionsResponse> listSessions(String requestedApp, String requestedUser) {
        return Single.fromCallable(() -> {
            requireIdentity(requestedApp, requestedUser);
            if (lease != null) throw leaseExpired();
            List<Session> sessions = read.execute(status -> {
                List<JdbcAdkSessionRepository.SessionRow> rows = repository.sessions(MAX_SESSIONS + 1);
                if (rows.size() > MAX_SESSIONS) throw capacity();
                return rows.stream().map(row -> Session.builder(row.id()).appName(appName).userId(userId)
                        .lastUpdateTime(row.updatedAt()).build()).toList();
            });
            return ListSessionsResponse.builder().sessions(sessions).build();
        });
    }

    @Override
    public Completable deleteSession(String requestedApp, String requestedUser, String sessionId) {
        return Completable.fromAction(() -> {
            requireIdentity(requestedApp, requestedUser);
            if (lease != null) throw leaseExpired();
            String id = sessionId(sessionId);
            write.executeWithoutResult(status -> {
                if (repository.session(id, true).isEmpty()) throw notFound();
                if (repository.lockLease(id).live() || ("tianji".equals(appName) && repository.hasActiveRun(id))) {
                    throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, "ADK session is already in use");
                }
                repository.delete(id);
            });
        });
    }

    @Override
    public Single<ListEventsResponse> listEvents(String requestedApp, String requestedUser, String sessionId) {
        return Single.fromCallable(() -> {
            requireIdentity(requestedApp, requestedUser);
            String id = sessionId(sessionId);
            List<Event> events = readableSession(id, row -> loadEvents(row, Optional.empty()));
            if (events == null) throw notFound();
            return ListEventsResponse.builder().events(events).build();
        });
    }

    @Override
    public Single<Event> appendEvent(Session session, Event event) {
        return Single.fromCallable(() -> {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(event, "event");
            requireIdentity(session.appName(), session.userId());
            String id = sessionId(session.id());
            if (lease == null || !lease.sessionId.equals(id)) throw leaseExpired();
            if (event.partial().orElse(false)) return event;
            String payload = eventCodec.encode(event);
            Event durable = eventCodec.decode(payload);
            synchronized (session) {
                Long revision = revisions.get(session);
                if (revision == null) throw revisionConflict();
                AppendResult result = write.execute(status -> append(id, revision, durable, payload));
                if (result.applyToSession()) {
                    BaseSessionService.super.appendEvent(session, durable).blockingGet();
                }
                session.lastUpdateTime(result.row().updatedAt());
                revisions.put(session, result.row().revision());
            }
            return event;
        });
    }

    private AppendResult append(String id, long expectedRevision, Event event, String payload) {
        requireRunFence(id, lease.runLease);
        JdbcAdkSessionRepository.SessionRow row = repository.session(id, true).orElseThrow(PostgresAdkSessionService::notFound);
        requireSessionFence(id, lease);
        Optional<JdbcAdkSessionRepository.EventRow> duplicate = repository.event(id, event.id(), payload);
        if (duplicate.isPresent()) {
            if (!duplicate.get().matches()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                        "ADK event identifier conflicts with stored content");
            }
            boolean apply = row.revision() == expectedRevision + 1
                    && duplicate.get().sequence() == row.lastEventSequence();
            if (!apply && row.revision() != expectedRevision) throw revisionConflict();
            requireLiveFences(id, lease);
            return new AppendResult(row, apply);
        }
        if (row.revision() != expectedRevision || row.lastEventSequence() == Long.MAX_VALUE) throw revisionConflict();
        Map<String, Object> local = localState(row.state());
        Map<String, Object> delta = event.actions().stateDelta();
        applyLocalState(local, delta);
        applyScopedState(delta);
        long sequence = row.lastEventSequence() + 1;
        repository.insertEvent(id, sequence, event.id(), event.timestamp(), payload);
        JdbcAdkSessionRepository.SessionRow updated = repository.updateSession(id, row.revision(), sequence,
                stateCodec.encode(local));
        requireLiveFences(id, lease);
        return new AppendResult(updated, true);
    }

    private <T> T readableSession(String id, Function<JdbcAdkSessionRepository.SessionRow, T> reader) {
        if (lease == null) {
            return read.execute(status -> repository.session(id, false).map(reader).orElse(null));
        }
        if (!lease.sessionId.equals(id)) throw leaseExpired();
        return write.execute(status -> {
            requireRunFence(id, lease.runLease);
            JdbcAdkSessionRepository.SessionRow row = repository.session(id, true)
                    .orElseThrow(PostgresAdkSessionService::notFound);
            requireSessionFence(id, lease);
            T result = reader.apply(row);
            requireLiveFences(id, lease);
            return result;
        });
    }

    private List<Event> loadEvents(JdbcAdkSessionRepository.SessionRow session, Optional<GetSessionConfig> config) {
        Integer recent = config.flatMap(GetSessionConfig::numRecentEvents).orElse(null);
        if (recent != null && (recent < 0 || recent > MAX_EVENTS)) throw capacity();
        if (recent != null && recent == 0) return List.of();
        Long after = config.flatMap(GetSessionConfig::afterTimestamp).map(Instant::toEpochMilli).orElse(null);
        int limit = recent == null ? MAX_EVENTS + 1 : recent;
        JdbcAdkSessionRepository.EventBounds all = repository.eventBounds(session.id(), limit, null);
        if (all.count() > MAX_EVENTS) throw capacity();
        long expectedCount = recent == null ? session.lastEventSequence() : Math.min(recent, session.lastEventSequence());
        if (all.count() != expectedCount || (expectedCount > 0
                && (all.firstSequence() != session.lastEventSequence() - expectedCount + 1
                || all.lastSequence() != session.lastEventSequence()))) throw corruptHistory();
        JdbcAdkSessionRepository.EventBounds selected = after == null ? all : repository.eventBounds(session.id(), limit, after);
        if (selected.bytes() > MAX_HISTORY_BYTES) throw capacity();
        List<JdbcAdkSessionRepository.EventRow> rows = repository.events(session.id(), limit, after);
        if (rows.size() != selected.count()) throw corruptHistory();
        if (after == null) {
            if (rows.size() != expectedCount) throw corruptHistory();
            long expectedSequence = session.lastEventSequence() - expectedCount;
            for (JdbcAdkSessionRepository.EventRow row : rows) {
                if (row.sequence() != ++expectedSequence) throw corruptHistory();
            }
        }
        return rows.stream().map(row -> {
            Event event = eventCodec.decode(row.payload());
            if (!row.nativeId().equals(event.id()) || row.timestampMillis() != event.timestamp()
                    || row.sequence() < 1 || row.sequence() > session.lastEventSequence()) throw corruptHistory();
            return event;
        }).toList();
    }

    private SessionView view(JdbcAdkSessionRepository.SessionRow row, List<Event> events) {
        requireScopeWithinBounds();
        Map<String, Object> state = localState(row.state());
        repository.scopeState().forEach((key, json) -> state.put(key, stateCodec.decodeValue(json)));
        Session session = Session.builder(row.id()).appName(appName).userId(userId).state(state)
                .events(events).lastUpdateTime(row.updatedAt()).build();
        return new SessionView(session, row.revision());
    }

    private Session register(SessionView view) {
        revisions.put(view.session(), view.revision());
        return view.session();
    }

    private Map<String, Object> localState(String json) {
        Map<String, Object> state = new HashMap<>(stateCodec.decodeMap(json));
        state.keySet().forEach(key -> {
            validateStateKey(key);
            if (key.startsWith(State.APP_PREFIX) || key.startsWith(State.USER_PREFIX) || key.startsWith(State.TEMP_PREFIX)) {
                throw corruptHistory();
            }
        });
        return state;
    }

    private void applyLocalState(Map<String, Object> local, Map<String, Object> delta) {
        sorted(delta).forEach((key, value) -> {
            validateStateKey(key);
            if (key.startsWith(State.APP_PREFIX) || key.startsWith(State.USER_PREFIX) || key.startsWith(State.TEMP_PREFIX)) return;
            if (value == null || value == State.REMOVED) local.remove(key); else local.put(key, value);
        });
    }

    private void applyScopedState(Map<String, Object> delta) {
        Map<String, Object> changes = sorted(delta);
        boolean hasScopedChanges = changes.keySet().stream()
                .anyMatch(key -> key.startsWith(State.APP_PREFIX) || key.startsWith(State.USER_PREFIX));
        if (hasScopedChanges) repository.lockScope();
        changes.forEach((key, value) -> {
            validateStateKey(key);
            boolean app = key.startsWith(State.APP_PREFIX);
            boolean user = key.startsWith(State.USER_PREFIX);
            if (!app && !user) return;
            if (app && !allowAppStateWrites) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "app state write is not authorized");
            }
            String name = key.substring(app ? State.APP_PREFIX.length() : State.USER_PREFIX.length());
            if (name.isEmpty()) throw invalid();
            String scope = app ? "APP" : "USER";
            if (value == null || value == State.REMOVED) repository.deleteScope(scope, name);
            else repository.putScope(scope, name, stateCodec.encode(value));
        });
        if (hasScopedChanges) requireScopeWithinBounds();
    }

    private void requireScopeWithinBounds() {
        JdbcAdkSessionRepository.ScopeBounds bounds = repository.scopeBounds();
        if (bounds.appCount() > MAX_SCOPE_ENTRIES || bounds.userCount() > MAX_SCOPE_ENTRIES
                || bounds.appBytes() > MAX_SCOPE_BYTES || bounds.userBytes() > MAX_SCOPE_BYTES) throw capacity();
    }

    private static Map<String, Object> sorted(Map<String, Object> value) {
        try { return new TreeMap<>(value); }
        catch (RuntimeException invalid) { throw invalid(); }
    }

    private static void validateStateKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 256 || key.indexOf('\0') >= 0) throw invalid();
    }

    private void requireRunFence(String id, AgentRunLease runLease) {
        if (!"tianji".equals(appName)) throw leaseExpired();
        JdbcAdkSessionRepository.RunFence run = repository.lockRun(runLease.runId()).orElseThrow(PostgresAdkSessionService::leaseExpired);
        Instant now = repository.databaseNow();
        if (!id.equals(run.sessionId()) || !"RUNNING".equals(run.status()) || run.cancelled()
                || !runLease.owner().equals(run.owner()) || runLease.epoch() != run.epoch()
                || run.expiresAt() == null || !run.expiresAt().isAfter(now) || !run.deadlineAt().isAfter(now)) {
            throw leaseExpired();
        }
    }

    private void requireSessionFence(String id, SessionLease expected) {
        JdbcAdkSessionRepository.LeaseRow current = repository.lockLease(id);
        if (!current.live() || current.expiresAt() == null || !current.expiresAt().isAfter(repository.databaseNow())
                || !expected.owner.equals(current.owner()) || expected.epoch != current.epoch()) throw leaseExpired();
    }

    private void requireLiveFences(String id, SessionLease expected) {
        if (!repository.liveFences(id, expected.runLease, expected.owner, expected.epoch)) throw leaseExpired();
    }

    private void requireLeaseScope(SessionLease value) {
        if (value == null || !actor.equals(value.actor) || !appName.equals(value.appName)) throw leaseExpired();
    }

    private void requireIdentity(String requestedApp, String requestedUser) {
        if (!appName.equals(requestedApp) || !userId.equals(requestedUser)) throw invalid();
    }

    private static String sessionId(String value) {
        if (value == null || value.isBlank() || value.length() > 128 || !value.equals(value.trim())) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) throw invalid();
        }
        return value;
    }

    private static boolean validApp(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]{1,128}");
    }

    private static long leaseMillis(Duration value) {
        if (value == null || value.compareTo(MIN_LEASE) < 0 || value.compareTo(MAX_LEASE) > 0) throw invalid();
        long millis = value.toMillis();
        if (!Duration.ofMillis(millis).equals(value)) throw invalid();
        return millis;
    }

    private static TransactionTemplate transaction(DataSourceTransactionManager manager, boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(readOnly ? TransactionDefinition.ISOLATION_REPEATABLE_READ
                : TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setReadOnly(readOnly);
        return template;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("ADK session request is invalid"); }
    private static SessionNotFoundException notFound() { return new SessionNotFoundException("ADK session not found"); }
    private static AgentLedgerException revisionConflict() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "ADK session snapshot is stale");
    }
    private static AgentLedgerException leaseExpired() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "ADK session or run lease is no longer valid");
    }
    private static AgentLedgerException capacity() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "ADK session history exceeds its safe limit");
    }
    private static IllegalStateException corruptHistory() { return new IllegalStateException("ADK session history is inconsistent"); }

    private record SessionView(Session session, long revision) { }
    private record AppendResult(JdbcAdkSessionRepository.SessionRow row, boolean applyToSession) { }

    /** Opaque capability; the owner token is never exposed or logged. */
    public static final class SessionLease {
        private final TrustedAgentActor actor;
        private final String appName;
        private final String sessionId;
        private final String owner;
        private final long epoch;
        private final Instant expiresAt;
        private final AgentRunLease runLease;

        private SessionLease(TrustedAgentActor actor, String appName, String sessionId, String owner,
                             long epoch, Instant expiresAt, AgentRunLease runLease) {
            this.actor = actor;
            this.appName = appName;
            this.sessionId = sessionId;
            this.owner = owner;
            this.epoch = epoch;
            this.expiresAt = expiresAt;
            this.runLease = runLease;
        }

        public String sessionId() { return sessionId; }
        public Instant expiresAt() { return expiresAt; }
    }
}
