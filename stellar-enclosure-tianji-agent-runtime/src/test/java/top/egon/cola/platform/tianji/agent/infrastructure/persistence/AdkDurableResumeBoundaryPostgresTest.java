package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.apps.App;
import com.google.adk.apps.ResumabilityConfig;
import com.google.adk.events.Event;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService.SessionLease;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optional storage/native compatibility probe, without a product checkpoint or Run handoff.
 * Two replacement SDK runners may use distinct Session leases while one original Run lease is still live.
 */
@EnabledIfSystemProperty(named = "tianji.postgres.resume.probe", matches = "true")
@Timeout(75)
class AdkDurableResumeBoundaryPostgresTest {
    private static final Pattern ISOLATED_URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_resume_[a-z0-9_]{1,32})");
    private static final AtomicLong TENANT = new AtomicLong(100_000);
    private static DriverManagerDataSource source;

    private static final String APP = "tianji";
    private static final String SESSION = "durable-resume-boundary";
    private static final String ROOT = "fixture_root";
    private static final String ORIGINAL = "inspect one isolated durable fixture";
    private static final String READ = "readFact";
    private static final String CALL = "fixture-read-call";
    private static final String RESULT = "continuation response";
    private static final long SECONDS = 5;
    private static final RunConfig CONFIG = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE)
            .setMaxLlmCalls(3).setAutoCreateSession(false).build();

    @Test
    void proposed_no_tool_pre_model_cut_can_be_tested_under_replacement_session_lease() {
        for (boolean typedWrites : List.of(false, true)) {
            var fixture = new Fixture(typedWrites);
            var interrupted = PublishProcessor.<Boolean>create();
            var originalModel = new ScriptModel(request -> {
                fail("pre-model cut must not invoke the fake model");
                return Flowable.empty();
            });
            var oldBound = fixture.store.withLease(fixture.oldLease);
            Runner original = runner(oldBound, originalModel, builder ->
                    builder.beforeModelCallback((context, request) -> Maybe.defer(() -> {
                        interrupted.onNext(true);
                        return Maybe.never();
                    })));
            try {
                collect(fresh(original, oldBound).takeUntil(interrupted));
            } finally {
                close(original);
            }
            assertEquals(0, originalModel.calls.get());
            var prefix = fixture.history();
            assertEquals(1, prefix.size(), "one stored input before a no-tool model call");
            Event saved = prefix.getFirst();
            String invocationId = saved.invocationId();
            assertFalse(invocationId.isBlank());
            assertTrue(originalInput(saved, invocationId));
            assertFalse(ended(prefix, invocationId));
            fixture.continueOnce(oldBound, invocationId, saved, null);
        }
    }

    @Test
    void committed_local_read_response_can_cross_a_replacement_session_lease_without_replay() {
        for (boolean typedWrites : List.of(false, true)) {
            var fixture = new Fixture(typedWrites);
            var localRead = new LocalRead();
            var originalModel = new ScriptModel(request -> Flowable.just(response(callPart())));
            var oldBound = fixture.store.withLease(fixture.oldLease);
            Runner original = runner(oldBound, originalModel, builder -> builder.tools(FunctionTool.create(localRead, READ)));
            try {
                collect(fresh(original, oldBound).takeUntil(event -> !event.functionResponses().isEmpty()));
            } finally {
                close(original);
            }
            var prefix = fixture.history();
            Event saved = prefix.stream().filter(event -> event.content().map(AdkDurableResumeBoundaryPostgresTest::hasReadResult)
                    .orElse(false)).findFirst().orElseThrow();
            String invocationId = saved.invocationId();
            assertEquals(1, originalModel.calls.get());
            assertEquals(1, localRead.calls.get());
            assertEquals(1L, prefix.stream().filter(event -> originalInput(event, invocationId)).count());
            assertFalse(ended(prefix, invocationId));
            fixture.continueOnce(oldBound, invocationId, saved, localRead);
            assertEquals(1, localRead.calls.get());
        }
    }

    @Test
    void an_expired_run_cannot_be_reclaimed_by_a_second_run_owner() {
        var fixture = new Fixture(false);
        var bound = fixture.store.withLease(fixture.oldLease);
        var query = new GetAgentRunQuery(fixture.run.runId());
        assertEquals(1, fixture.jdbc.update("""
                UPDATE tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second'
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?
                  AND status='RUNNING' AND lease_owner=? AND lease_epoch=?
                """, fixture.actor.tenantId(), fixture.actor.subjectType().name(), fixture.actor.issuerFingerprint(),
                fixture.actor.subjectId(), fixture.run.runId(), fixture.run.owner(), fixture.run.epoch()));
        var stale = fixture.ledger.get(fixture.actor, query);
        var suspended = fixture.ledger.suspendExpiredLease(fixture.actor, query, stale.revision());
        assertEquals(AgentRunStatusEnum.SUSPENDED, suspended.status());
        assertEquals("LEASE_EXPIRED", suspended.waitReason());
        assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> fixture.ledger.claim(fixture.actor, query, suspended.revision(),
                        "replacement-run-owner", Duration.ofSeconds(30))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> fixture.store.acquireSessionLease(SESSION, fixture.run, Duration.ofSeconds(30))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> bound.getSession(APP, bound.adkUserId(), SESSION, Optional.empty()).blockingGet()).code());
        assertTrue(fixture.ledger.getResult(fixture.actor, query).isEmpty());
        assertEquals(AgentRunStatusEnum.SUSPENDED, fixture.ledger.get(fixture.actor, query).status());
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = new JdbcTemplate(isolatedSource());
        private final AgentLedgerService ledger = new AgentLedgerService(isolatedSource());
        private final TrustedAgentActor actor = new TrustedAgentActor(TENANT.incrementAndGet(),
                TrustedAgentActor.SubjectType.USER, "native-resume-probe", "a".repeat(64));
        private final boolean typedWrites;
        private final PostgresAdkSessionService store;
        private final AgentRunLease run;
        private final SessionLease oldLease;

        Fixture(boolean typedWrites) {
            this.typedWrites = typedWrites;
            store = newStore();
            assertNotNull(store.createSession(APP, store.adkUserId(), Map.of(), SESSION)
                    .timeout(SECONDS, TimeUnit.SECONDS).blockingGet());
            ledger.publish(actor, AgentJsonCodecTest.spec(1, Map.of(), Set.of()));
            var queued = ledger.start(actor, new StartAgentRunCommand("review", 1, SESSION, "native-boundary-run",
                    null, null, new AgentInputDTO(ORIGINAL, List.of(), Map.of()), null));
            run = ledger.claim(actor, new GetAgentRunQuery(queued.runId()), queued.revision(),
                    "original-run-owner", Duration.ofSeconds(45)).lease();
            oldLease = store.acquireSessionLease(SESSION, run, Duration.ofSeconds(40));
        }

        private PostgresAdkSessionService newStore() {
            return new PostgresAdkSessionService(isolatedSource(), actor, APP, false, typedWrites);
        }

        private List<Event> history() {
            return read(store).immutableEvents();
        }

        private void continueOnce(PostgresAdkSessionService oldBound, String invocationId, Event saved, LocalRead localRead) {
            Session stale = read(oldBound);
            var oldFact = leaseFact();
            var bytesBefore = durablePrefix();
            assertEquals(1, jdbc.update("""
                    UPDATE tianji_agent.agent_session_lease SET lease_expires_at=clock_timestamp()-interval '1 second'
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                      AND lease_owner=? AND lease_epoch=?
                    """, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    APP, SESSION, oldFact.owner(), oldFact.epoch()));
            var successor = newStore();
            var nextLease = successor.acquireSessionLease(SESSION, run, Duration.ofSeconds(30));
            try {
                var nextFact = leaseFact();
                assertEquals(oldFact.epoch() + 1, nextFact.epoch());
                assertTrue(nextFact.owner() != null && !nextFact.owner().equals(oldFact.owner()),
                        "distinct opaque Session lease owners");
                assertFalse(store.releaseSessionLease(oldLease));
                assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                        () -> oldBound.appendEvent(stale, saved).blockingGet()).code());
                var bound = successor.withLease(nextLease);
                assertEquals(bytesBefore, durablePrefix());
                assertEquals(AgentRunStatusEnum.RUNNING, ledger.get(actor, new GetAgentRunQuery(run.runId())).status());
                var nextModel = new ScriptModel(request -> {
                    assertTrue(request.contents().stream().anyMatch(content -> hasText(content, ORIGINAL)));
                    if (localRead != null) assertTrue(request.contents().stream().anyMatch(AdkDurableResumeBoundaryPostgresTest::hasReadResult));
                    return Flowable.just(response(Part.fromText(RESULT)));
                });
                Runner replacement = runner(bound, nextModel, builder -> {
                    if (localRead != null) builder.tools(FunctionTool.create(localRead, READ));
                });
                try {
                    var output = collect(explicit(replacement, bound, invocationId));
                    assertFalse(output.isEmpty());
                    assertTrue(output.stream().allMatch(event -> invocationId.equals(event.invocationId())));
                    assertTrue(output.stream().anyMatch(event -> event.content().map(content -> hasText(content, RESULT)).orElse(false)));
                    assertEquals(1, nextModel.calls.get());
                    var persisted = read(successor).immutableEvents();
                    assertEquals(1L, persisted.stream().filter(event -> originalInput(event, invocationId)).count());
                    assertTrue(ended(persisted, invocationId));
                    var bytesAfter = durablePrefix();
                    assertTrue(bytesAfter.size() >= bytesBefore.size(), "stored history must retain its complete prior prefix");
                    assertEquals(bytesBefore, bytesAfter.subList(0, bytesBefore.size()));
                    assertTrue(collect(explicit(replacement, bound, invocationId)).isEmpty());
                    assertEquals(bytesAfter, durablePrefix());
                    assertEquals(1, nextModel.calls.get());
                    assertTrue(ledger.getResult(actor, new GetAgentRunQuery(run.runId())).isEmpty(),
                            "native Session completion is not a product Run terminal result");
                } finally {
                    close(replacement);
                }
            } finally {
                assertTrue(successor.releaseSessionLease(nextLease));
            }
        }

        private LeaseFact leaseFact() {
            return jdbc.queryForObject("""
                    SELECT lease_owner,lease_epoch FROM tianji_agent.agent_session_lease
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                    """, (row, index) -> new LeaseFact(row.getString(1), row.getLong(2)),
                    actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), APP, SESSION);
        }

        private List<String> durablePrefix() {
            var result = jdbc.query("""
                    SELECT sequence,native_event_id,event_timestamp_ms,payload FROM tianji_agent.agent_session_event
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                    ORDER BY sequence LIMIT 17
                    """, (row, index) -> {
                        assertEquals(index + 1L, row.getLong(1));
                        return row.getString(2) + ':' + row.getLong(3) + ':' + sha256(row.getString(4));
                    }, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), APP, SESSION);
            assertTrue(result.size() <= 16);
            return result;
        }
    }

    private static synchronized DriverManagerDataSource isolatedSource() {
        String url = System.getProperty("tianji.postgres.resume.probe.url", "");
        var binding = ISOLATED_URL.matcher(url);
        if (!Boolean.getBoolean("tianji.postgres.resume.probe") || !binding.matches()
                || Integer.parseInt(binding.group(1)) > 65535) {
            throw new IllegalArgumentException("resume probe requires an explicit disposable loopback tianji_it_resume_ database");
        }
        if (source != null) {
            if (!url.equals(source.getUrl())) throw new IllegalStateException("resume probe database changed");
            return source;
        }
        var candidate = new DriverManagerDataSource();
        candidate.setUrl(url);
        var limits = new Properties();
        limits.setProperty("connectTimeout", "3");
        limits.setProperty("loginTimeout", "3");
        limits.setProperty("socketTimeout", "5");
        limits.setProperty("options", "-c statement_timeout=5000 -c lock_timeout=2000");
        candidate.setConnectionProperties(limits);
        var database = new JdbcTemplate(candidate);
        if (!Boolean.TRUE.equals(database.queryForObject("""
                SELECT current_database()=? AND NOT pg_is_in_recovery()
                  AND NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='tianji_agent')
                """, Boolean.class, binding.group(2)))) {
            throw new IllegalStateException("resume probe requires a fresh primary database without a Tianji schema");
        }
        var flyway = Flyway.configure().dataSource(candidate).schemas("tianji_agent")
                .defaultSchema("tianji_agent").locations("classpath:db/tianji-agent").target("9")
                .cleanDisabled(true).lockRetryCount(0).load();
        flyway.migrate();
        flyway.validate();
        source = candidate;
        return source;
    }

    private static Session read(PostgresAdkSessionService store) {
        return store.getSession(APP, store.adkUserId(), SESSION, Optional.empty())
                .timeout(SECONDS, TimeUnit.SECONDS).blockingGet();
    }

    private static Runner runner(PostgresAdkSessionService bound, ScriptModel model, Consumer<LlmAgent.Builder> customize) {
        var builder = LlmAgent.builder().name(ROOT).description("isolated storage probe")
                .instruction("Inspect only the deterministic local fixture.").model(model).executor(Runnable::run);
        customize.accept(builder);
        var app = App.builder().name(APP).rootAgent(builder.build()).resumabilityConfig(ResumabilityConfig.builder()
                .resumable(true).plainTextContinuationAutoResume(false).build()).build();
        return Runner.builder().app(app).sessionService(bound).build();
    }

    private static Flowable<Event> fresh(Runner runner, PostgresAdkSessionService bound) {
        return runner.runAsync(bound.adkUserId(), SESSION,
                Content.builder().role("user").parts(Part.fromText(ORIGINAL)).build(), CONFIG);
    }

    private static Flowable<Event> explicit(Runner runner, PostgresAdkSessionService bound, String id) {
        return runner.runAsync(bound.adkUserId(), SESSION, id, null, CONFIG, Map.of());
    }

    private static List<Event> collect(Flowable<Event> flow) {
        return flow.toList().timeout(SECONDS, TimeUnit.SECONDS).blockingGet();
    }

    private static void close(Runner runner) {
        runner.close().timeout(SECONDS, TimeUnit.SECONDS).blockingAwait();
    }

    private static Part callPart() {
        return Part.builder().functionCall(FunctionCall.builder().id(CALL).name(READ).args(Map.of()).build()).build();
    }

    private static LlmResponse response(Part part) {
        return LlmResponse.builder().content(Content.builder().role("model").parts(part).build())
                .partial(false).turnComplete(true).build();
    }

    private static boolean hasText(Content content, String expected) {
        return content.parts().orElse(List.of()).stream().flatMap(part -> part.text().stream()).anyMatch(expected::equals);
    }

    private static boolean hasReadResult(Content content) {
        return content.parts().orElse(List.of()).stream().flatMap(part -> part.functionResponse().stream())
                .anyMatch(response -> response.id().filter(CALL::equals).isPresent()
                        && response.response().map(value -> "read-only".equals(value.get("value"))).orElse(false));
    }

    private static boolean originalInput(Event event, String invocation) {
        return invocation.equals(event.invocationId()) && "user".equals(event.author())
                && event.content().map(content -> hasText(content, ORIGINAL)).orElse(false);
    }

    private static boolean ended(List<Event> events, String invocation) {
        return events.stream().anyMatch(event -> invocation.equals(event.invocationId())
                && ROOT.equals(event.author()) && event.actions().endOfAgent());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError("SHA-256 unavailable");
        }
    }

    private record LeaseFact(String owner, long epoch) {
        @Override public String toString() { return "LeaseFact[redacted]"; }
    }

    private static final class ScriptModel extends BaseLlm {
        private final AtomicInteger calls = new AtomicInteger();
        private final Function<LlmRequest, Flowable<LlmResponse>> script;

        private ScriptModel(Function<LlmRequest, Flowable<LlmResponse>> script) {
            super("checkpoint-proof-fake");
            this.script = script;
        }

        @Override public Flowable<LlmResponse> generateContent(LlmRequest request, boolean streaming) {
            assertTrue(streaming);
            calls.incrementAndGet();
            return script.apply(request);
        }

        @Override public BaseLlmConnection connect(LlmRequest request) {
            throw new AssertionError("live model connection is outside this probe");
        }
    }

    public static final class LocalRead {
        private final AtomicInteger calls = new AtomicInteger();

        public Map<String, Object> readFact() {
            calls.incrementAndGet();
            return Map.of("value", "read-only");
        }
    }
}
