package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentContextRuntimeOptions;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit isolated PostgreSQL proof of first-input capture and its transaction failure boundary. */
@EnabledIfSystemProperty(named = "tianji.postgres.checkpoint.capture", matches = "true")
@Timeout(120)
class AgentCheckpointCapturePostgresTest {
    private static final Pattern URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_checkpoint_[a-z0-9_]{1,32})");
    private static final String APP = "tianji";
    private static final String SESSION = "checkpoint-probe-session";
    private static final String DEFINITION = "checkpoint-probe";
    private static final String INPUT = "Read the disposable checkpoint fixture.";
    private static final String OUTPUT = "Checkpoint fixture completed.";
    private static final String MODEL = "checkpoint-fake";
    private static final String ROUTE = "checkpoint-fixture-route";
    private static final String POLICY = "checkpoint-fixture-policy";
    private static final String CHECKPOINT = "tianji_agent.agent_checkpoint";
    private static final String HISTORY = """
            SELECT version,script,type,checksum,success FROM tianji_agent.flyway_schema_history
            WHERE version IS NOT NULL ORDER BY installed_rank
            """;
    private static final String SESSION_SCOPE = """
            WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
            """;

    @Test
    void captures_first_input_before_budget_and_model_and_rolls_back_if_checkpoint_insert_is_denied() {
        var database = provision();
        var setupLedger = new AgentLedgerService(database.setup());
        var runtimeLedger = new AgentLedgerService(database.runtime());
        var factory = new AgentFlowFactory(new SpringAiModelAdapterFactory(), new AgentWorkflowStrategyFactory(List.of()));
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var validator = new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator()));
            var control = Executors.newScheduledThreadPool(2,
                    task -> Thread.ofPlatform().daemon().name("checkpoint-probe-control").unstarted(task));
            try {
                long tenant = 90_000;
                for (boolean typed : List.of(false, true)) {
                    var success = new RunFixture(database, setupLedger, ++tenant, typed);
                    var successModel = new FakeModel();
                    var successEstimates = new AtomicInteger();
                    var successEstimator = estimator(() -> {
                        successEstimates.incrementAndGet();
                        assertEquals(0, successModel.streams.get());
                        success.assertCommittedCut();
                    });
                    var successWorker = AdkAgentRunExecutor.forRuntime(runtimeLedger, factory, validator,
                            Map.of("fixtureModel", successModel), Map.of(), control, successEstimator,
                            database.runtime(), typed, success::authorize, new DefaultContextAssembler(),
                            new AgentContextRuntimeOptions(false, false), null, true);
                    assertEquals(AgentRunStatusEnum.COMPLETED, successWorker.executeQueued(success.actor, success.query).status());
                    assertEquals(1, successEstimates.get());
                    assertEquals(1, successModel.streams.get());
                    assertEquals(0, successModel.calls.get());
                    assertEquals(OUTPUT, setupLedger.getResult(success.actor, success.query).orElseThrow().output().get("text"));
                    assertEquals(AgentRunStatusEnum.COMPLETED, successWorker.executeQueued(success.actor, success.query).status());
                    assertEquals(1, successModel.streams.get());
                    assertEquals(1, success.checkpoints());

                    var failure = new RunFixture(database, setupLedger, ++tenant, typed);
                    var failureModel = new FakeModel();
                    var failureEstimates = new AtomicInteger();
                    var failureWorker = AdkAgentRunExecutor.forRuntime(runtimeLedger, factory, validator,
                            Map.of("fixtureModel", failureModel), Map.of(), control,
                            estimator(failureEstimates::incrementAndGet),
                            database.runtime(), typed, failure::authorize, new DefaultContextAssembler(),
                            new AgentContextRuntimeOptions(false, false), null, true);
                    database.observer().execute("REVOKE INSERT ON " + CHECKPOINT + " FROM " + database.runtimeRole());
                    try {
                        assertFalse(new JdbcTemplate(database.runtime()).queryForObject(
                                "SELECT has_table_privilege(current_user,?,'INSERT')", Boolean.class, CHECKPOINT));
                        assertEquals(AgentRunStatusEnum.FAILED, failureWorker.executeQueued(failure.actor, failure.query).status());
                        var failureResult = setupLedger.getResult(failure.actor, failure.query).orElseThrow();
                        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name(), failureResult.errorCode());
                        assertTrue(failureResult.output().isEmpty());
                        assertEquals(0, failureEstimates.get());
                        assertEquals(0, failureModel.streams.get());
                        assertEquals(0, failureModel.calls.get());
                        failure.assertEmptySession();
                    } finally {
                        database.observer().execute("GRANT INSERT ON " + CHECKPOINT + " TO " + database.runtimeRole());
                    }
                }
            } finally {
                control.shutdownNow();
            }
        }
    }

    private static Database provision() {
        String url = System.getProperty("tianji.postgres.checkpoint.capture.url", "");
        String role = System.getProperty("tianji.postgres.checkpoint.capture.runtime-role", "");
        String vectorVersion = System.getProperty("tianji.postgres.checkpoint.capture.vector.version", "");
        var match = URL.matcher(url);
        if (!Boolean.getBoolean("tianji.postgres.checkpoint.capture") || !match.matches()
                || Integer.parseInt(match.group(1)) > 65_535 || !role.matches("tianji_it_checkpoint_[a-z0-9_]{1,32}")
                || !vectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("checkpoint probe requires explicit disposable loopback database, runtime role and vector patch");
        }
        var setup = dataSource(url, null);
        var observer = new JdbcTemplate(setup);
        observer.setQueryTimeout(5);
        assertTrue(Boolean.TRUE.equals(observer.queryForObject("""
                SELECT current_database()=? AND inet_server_addr()=inet '127.0.0.1' AND NOT pg_is_in_recovery()
                  AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'
                  AND current_setting('transaction_read_only')='off'
                  AND NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='tianji_agent')
                """, Boolean.class, match.group(2))));
        assertEquals(vectorVersion, observer.queryForObject("""
                SELECT e.extversion FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
                WHERE e.extname='vector' AND n.nspname='public'
                """, String.class));
        assertTrue(Boolean.TRUE.equals(observer.queryForObject("""
                SELECT current_user<>? AND EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname=?
                    AND r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls AND NOT r.rolcreatedb
                    AND NOT r.rolcreaterole AND NOT r.rolreplication
                    AND NOT EXISTS (SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid))
                """, Boolean.class, role, role)));

        var previous = flyway(setup, "15");
        assertEquals(15, previous.migrate().migrationsExecuted);
        previous.validate();
        var originalHistory = observer.queryForList(HISTORY);
        assertEquals(15, originalHistory.size());
        var current = flyway(setup, "16");
        assertEquals(1, current.migrate().migrationsExecuted);
        current.validate();
        var history = observer.queryForList(HISTORY);
        assertEquals(16, history.size());
        assertEquals(originalHistory, history.subList(0, 15));
        assertEquals(Map.of("version", "16", "script", "V16__add_agent_checkpoint.sql", "type", "SQL",
                "checksum", -415542202, "success", true), history.getLast());

        String quotedRole = '"' + role + '"';
        observer.execute("GRANT USAGE ON SCHEMA tianji_agent TO " + quotedRole);
        observer.execute("""
                GRANT SELECT ON tianji_agent.flyway_schema_history,tianji_agent.agent_definition,
                  tianji_agent.agent_definition_version,tianji_agent.agent_run,tianji_agent.agent_event,
                  tianji_agent.agent_run_command_receipt,tianji_agent.agent_budget_reservation,
                  tianji_agent.agent_session,tianji_agent.agent_session_event,tianji_agent.agent_session_scope_state,
                  tianji_agent.agent_session_lease,tianji_agent.agent_run_execution_grant TO
                """ + quotedRole);
        observer.execute("""
                GRANT UPDATE ON tianji_agent.agent_run,tianji_agent.agent_session TO
                """ + quotedRole);
        observer.execute("""
                GRANT INSERT ON tianji_agent.agent_event,tianji_agent.agent_session_event TO
                """ + quotedRole);
        observer.execute("""
                GRANT INSERT, UPDATE ON tianji_agent.agent_budget_reservation,tianji_agent.agent_session_lease TO
                """ + quotedRole);
        observer.execute("GRANT INSERT ON " + CHECKPOINT + " TO " + quotedRole);
        var runtime = dataSource(url, role);
        assertTrue(new JdbcTemplate(runtime).queryForObject("SELECT current_user=?", Boolean.class, role));
        assertDoesNotThrow(new AgentStorageVerifier(runtime)::verifyCheckpointCapture);
        return new Database(setup, runtime, observer, quotedRole);
    }

    private static DriverManagerDataSource dataSource(String url, String username) {
        var dataSource = new DriverManagerDataSource(url);
        if (username != null) dataSource.setUsername(username);
        var limits = new Properties();
        limits.setProperty("connectTimeout", "3");
        limits.setProperty("loginTimeout", "3");
        limits.setProperty("socketTimeout", "8");
        limits.setProperty("options", "-c statement_timeout=6000 -c lock_timeout=2000");
        dataSource.setConnectionProperties(limits);
        return dataSource;
    }

    private static Flyway flyway(DriverManagerDataSource source, String version) {
        return Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                .target(version).cleanDisabled(true).lockRetryCount(0).load();
    }

    private static AgentDefinitionSpecDTO definition() {
        var dependencies = new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), ROUTE, POLICY);
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(35), Duration.ofSeconds(8), Duration.ofSeconds(2),
                1, 0, 100, 50, BigDecimal.ONE, "USD", 1, Set.of(), "checkpoint-fixture-retry", null);
        return new AgentDefinitionSpecDTO(DEFINITION, 1, "CONFIG", null, "1", "1", dependencies, policy,
                Map.of("chatModelBeanName", "fixtureModel", "modelName", MODEL, "rootAgentName", "writer",
                        "agents", List.of(Map.of("name", "writer", "instruction", "Complete the disposable fixture."))));
    }

    private static AgentModelBudgetEstimator estimator(Runnable beforeEstimate) {
        return new AgentModelBudgetEstimator() {
            @Override public Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request) {
                beforeEstimate.run();
                return new Estimate(10, 5, BigDecimal.ZERO);
            }

            @Override public ContextWindow contextWindow(AgentExecutionSnapshot snapshot, LlmRequest request) {
                return new ContextWindow(MODEL, ROUTE, POLICY, 1000, 5);
            }

            @Override public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response) {
                return new AgentBudgetSettlement(10L, 3L, BigDecimal.ZERO);
            }
        };
    }

    private record Database(DriverManagerDataSource setup, DriverManagerDataSource runtime,
                            JdbcTemplate observer, String runtimeRole) { }

    private static final class RunFixture {
        private final Database database;
        private final TrustedAgentActor actor;
        private final GetAgentRunQuery query;
        private final PostgresAdkSessionService sessions;
        private final AgentRunAuthorization.Request authorization;
        private final int codec;

        RunFixture(Database database, AgentLedgerService setupLedger, long tenant, boolean typed) {
            this.database = database;
            this.actor = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.USER,
                    "checkpoint-fixture-owner", "https://checkpoint-fixture.example");
            this.codec = typed ? 2 : 1;
            this.sessions = new PostgresAdkSessionService(database.setup(), actor, APP, false, typed);
            assertNotNull(sessions.createSession(APP, sessions.adkUserId(), Map.of(), SESSION)
                    .timeout(5, TimeUnit.SECONDS).blockingGet());
            setupLedger.publish(actor, definition());
            var run = setupLedger.start(actor, new StartAgentRunCommand(DEFINITION, 1, SESSION, "first-input",
                    null, null, new AgentInputDTO(INPUT, List.of(), Map.of()), null));
            this.query = new GetAgentRunQuery(run.runId());
            this.authorization = setupLedger.executionMaterial(actor, query).authorization();
            assertEmptySession();
        }

        AgentRunAuthorization.Decision authorize(AgentRunAuthorization.Request request, AgentRunAuthorization.Boundary boundary) {
            return authorization.equals(request) ? AgentRunAuthorization.Decision.ALLOW : AgentRunAuthorization.Decision.DENY;
        }

        private Object[] scope() {
            return new Object[]{actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), APP, SESSION};
        }

        int checkpoints() {
            return database.observer().queryForObject("SELECT count(*) FROM " + CHECKPOINT + " WHERE tenant_id=? AND run_id=?",
                    Integer.class, actor.tenantId(), query.runId());
        }

        private int reservations() {
            return database.observer().queryForObject("""
                    SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=?
                    """, Integer.class, actor.tenantId(), query.runId());
        }

        void assertEmptySession() {
            assertEquals(0, checkpoints());
            assertEquals(0, reservations());
            assertEquals(0, database.observer().queryForObject(
                    "SELECT count(*) FROM tianji_agent.agent_session_event " + SESSION_SCOPE, Integer.class, scope()));
            assertTrue(Boolean.TRUE.equals(database.observer().queryForObject("""
                    SELECT revision=0 AND last_event_sequence=0 FROM tianji_agent.agent_session
                    """ + SESSION_SCOPE, Boolean.class, scope())));
        }

        void assertCommittedCut() {
            assertEquals(1, checkpoints());
            assertEquals(0, reservations());
            var session = sessions.getSession(APP, sessions.adkUserId(), SESSION, Optional.empty())
                    .timeout(5, TimeUnit.SECONDS).blockingGet();
            assertNotNull(session);
            var history = session.immutableEvents();
            assertEquals(1, history.size());
            var original = history.getFirst();
            assertEquals("user", original.author());
            var content = original.content().orElseThrow();
            assertEquals("user", content.role().orElseThrow());
            var parts = content.parts().orElseThrow();
            assertEquals(1, parts.size());
            assertEquals(INPUT, parts.getFirst().text().orElseThrow());
            assertTrue(Boolean.TRUE.equals(database.observer().queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM tianji_agent.agent_checkpoint c
                        JOIN tianji_agent.agent_run r ON (r.tenant_id,r.run_id)=(c.tenant_id,c.run_id)
                        JOIN tianji_agent.agent_session s ON
                          (s.tenant_id,s.subject_type,s.subject_issuer,s.subject_id,s.app_name,s.session_id)=
                          (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id)
                        JOIN tianji_agent.agent_session_event e ON
                          (e.tenant_id,e.subject_type,e.subject_issuer,e.subject_id,e.app_name,e.session_id,e.sequence)=
                          (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id,c.session_event_sequence)
                        JOIN tianji_agent.agent_session_lease l ON
                          (l.tenant_id,l.subject_type,l.subject_issuer,l.subject_id,l.app_name,l.session_id)=
                          (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id)
                        WHERE c.tenant_id=? AND c.run_id=? AND c.subject_type=? AND c.subject_issuer=? AND c.subject_id=?
                          AND c.app_name=? AND c.session_id=? AND r.status='RUNNING' AND r.session_id=c.session_id
                          AND c.attempt=1 AND c.attempt=r.attempt AND c.checkpoint_no=1
                          AND c.run_lease_epoch=r.lease_epoch AND c.run_revision=r.revision
                          AND c.run_event_sequence=r.last_event_sequence AND c.session_lease_epoch=l.lease_epoch
                          AND c.prior_session_revision=0 AND c.prior_session_event_sequence=0
                          AND c.session_revision=1 AND c.session_revision=s.revision
                          AND c.session_event_sequence=1 AND c.session_event_sequence=s.last_event_sequence
                          AND s.state_codec_version=? AND (e.payload::jsonb ->> 'codecVersion')::smallint=?
                          AND c.native_invocation_id=? AND c.native_event_id=?
                          AND c.native_event_id=e.native_event_id AND c.definition_key=? AND c.definition_version=?
                          AND c.definition_sha256=? AND c.request_sha256=? AND c.request_sha256=r.request_hash
                          AND c.snapshot_sha256=? AND c.policy_version_ref=?
                          AND c.engine_version='1.13.0' AND c.safe_boundary='FIRST_DURABLE_INPUT_V1'
                          AND c.tool_receipts='[]'::jsonb AND c.captured_at BETWEEN r.created_at AND clock_timestamp())
                    """, Boolean.class, actor.tenantId(), query.runId(), actor.subjectType().name(),
                    actor.issuerFingerprint(), actor.subjectId(), APP, SESSION, codec, codec, original.invocationId(), original.id(),
                    authorization.definitionKey(), authorization.definitionVersion(), authorization.definitionSha256(),
                    authorization.requestSha256(), authorization.snapshotSha256(), authorization.policyVersionRef())));
        }
    }

    private static final class FakeModel implements ChatModel {
        final AtomicInteger streams = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        private final ChatOptions options = ToolCallingChatOptions.builder().model(MODEL).build();

        @Override public ChatOptions getOptions() { return options; }

        @Override public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            throw new AssertionError("checkpoint probe only uses the bounded fake stream");
        }

        @Override public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                streams.incrementAndGet();
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(OUTPUT),
                        ChatGenerationMetadata.builder().finishReason("stop").build()))));
            });
        }
    }
}
