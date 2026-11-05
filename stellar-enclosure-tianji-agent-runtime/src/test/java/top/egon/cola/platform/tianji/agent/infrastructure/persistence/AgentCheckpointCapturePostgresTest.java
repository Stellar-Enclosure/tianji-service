package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowProperties;
import top.egon.cola.component.agentflow.config.AgentFlowConfigDTO;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.AgentFlowSessionRunner;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentContextRuntimeOptions;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointObservationService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointObservationService.Observation;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumePermit;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointForkHandoff.Phase.CAPTURE;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointForkHandoff.Phase.NATIVE_RESUME;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointForkHandoff.Phase.RECONCILE;

/** Explicit isolated PostgreSQL proof of first-input capture and its transaction failure boundary. */
@EnabledIfSystemProperty(named = "tianji.postgres.checkpoint.capture", matches = "true")
@Timeout(120)
class AgentCheckpointCapturePostgresTest {
    private static final Pattern URL = Pattern.compile(
            "jdbc:postgresql://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/(tianji_it_checkpoint_[a-z0-9_]{1,32})");
    private static final String FORK_CHILD_SWITCH = "tianji.postgres.checkpoint.capture.fork.child";
    private static final String FORK_DIRECTORY_PREFIX = "tianji-checkpoint-fork-";
    private static final long FORK_CHILD_SECONDS = 16;
    private static final String APP = "tianji";
    private static final String SESSION = "checkpoint-probe-session";
    private static final String DEFINITION = "checkpoint-probe";
    private static final String INPUT = "Read the disposable checkpoint fixture.";
    private static final String OUTPUT = "Checkpoint fixture completed.";
    private static final String MODEL = "checkpoint-fake";
    private static final String ROUTE = "checkpoint-fixture-route";
    private static final String POLICY = "checkpoint-fixture-policy";
    private static final String CHECKPOINT = "tianji_agent.agent_checkpoint";
    private static final RunConfig NATIVE_RESUME_CONFIG = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE)
            .setMaxLlmCalls(1).setAutoCreateSession(false).build();
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

    @Test
    @Timeout(150)
    @EnabledIfSystemProperty(named = "tianji.postgres.checkpoint.capture.fork", matches = "true")
    void forced_process_exit_preserves_checkpoint_and_reconciles_expired_run_without_execution() throws Exception {
        verifyProductCheckpointForks(provision(), RECONCILE, (fixture, cut) -> { });
    }

    @Test
    @Timeout(150)
    @EnabledIfSystemProperty(named = "tianji.postgres.checkpoint.observation", matches = "true")
    void observes_only_exact_owned_product_checkpoint_after_reconciliation_and_rejects_evidence_drift() throws Exception {
        String readerRole = System.getProperty("tianji.postgres.checkpoint.observation.reader-role", "");
        if (!readerRole.matches("tianji_it_checkpoint_observer_[a-z0-9_]{1,24}")) {
            throw new IllegalArgumentException("checkpoint observation requires an exact disposable reader role");
        }
        var database = provision(readerRole);
        var readSource = dataSource(database.setup().getUrl(), readerRole);
        var readerJdbc = new JdbcTemplate(readSource);
        assertCheckpointReaderPrivileges(readerJdbc);
        var service = new AgentCheckpointObservationService(readSource);
        verifyProductCheckpointForks(database, RECONCILE, (fixture, cut) ->
                fixture.assertCheckpointObservation(cut, service, readerJdbc, '"' + readerRole + '"'));
    }

    @Test
    @Timeout(150)
    @EnabledIfSystemProperty(named = "tianji.postgres.checkpoint.capture.native-resume", matches = "true")
    void resumes_product_first_input_checkpoint_after_process_death_under_original_run_lease() throws Exception {
        verifyProductCheckpointForks(provision(), NATIVE_RESUME, (fixture, cut) -> { });
    }

    @Test
    @Timeout(150)
    @EnabledIfSystemProperty(named = "tianji.postgres.checkpoint.takeover", matches = "true")
    void takes_over_reconciled_product_checkpoint_once_under_scoped_resume_permit() throws Exception {
        String role = System.getProperty("tianji.postgres.checkpoint.takeover.role", "");
        if (!role.matches("tianji_it_checkpoint_takeover_[a-z0-9_]{1,24}")) {
            throw new IllegalArgumentException("checkpoint takeover requires an exact disposable takeover role");
        }
        // Reuse the existing third-role checks and V16 SELECT-only grants before adding bounded claim DML.
        var database = provision(role);
        var takeoverSource = dataSource(database.setup().getUrl(), role);
        var takeoverJdbc = new JdbcTemplate(takeoverSource);
        assertCheckpointReaderPrivileges(takeoverJdbc);
        grantCheckpointTakeover(database.observer(), '"' + role + '"');
        assertTrue(Boolean.TRUE.equals(takeoverJdbc.queryForObject("""
                SELECT current_user=? AND has_table_privilege('tianji_agent.agent_checkpoint','SELECT')
                  AND NOT has_any_column_privilege('tianji_agent.agent_checkpoint','INSERT,UPDATE,REFERENCES')
                  AND NOT has_table_privilege('tianji_agent.agent_checkpoint','DELETE,TRUNCATE,TRIGGER')
                """, Boolean.class, role)));
        verifyProductCheckpointForks(database, RECONCILE, (fixture, cut) -> fixture.assertCheckpointTakeover(cut, takeoverSource));
    }

    private static void verifyProductCheckpointForks(Database database, AgentCheckpointForkHandoff.Phase replacementPhase,
                                                     BiConsumer<RunFixture, ForkCut> afterReconcile) throws Exception {
        var setupLedger = new AgentLedgerService(database.setup());
        String setupRole = database.observer().queryForObject("SELECT current_user", String.class);
        String runtimeRole = database.runtime().getUsername();
        String vectorVersion = System.getProperty("tianji.postgres.checkpoint.capture.vector.version");
        for (boolean typed : List.of(false, true)) {
            var fixture = new RunFixture(database, setupLedger, AgentCheckpointForkHandoff.tenant(typed),
                    typed, Duration.ofMinutes(3), null);
            var directory = Files.createTempDirectory(FORK_DIRECTORY_PREFIX,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();
            var captureMarker = directory.resolve("capture");
            var replacementMarker = directory.resolve(replacementPhase.markerName());
            try {
                String url = database.setup().getUrl();
                var capture = new AgentCheckpointForkHandoff.Request(url, setupRole, runtimeRole, vectorVersion,
                        typed, fixture.query.runId(), CAPTURE, null);
                var original = startForkChild(capture, captureMarker);
                long originalPid = original.pid();
                ForkCut cut;
                try {
                    String acknowledgement = awaitForkCapture(original, captureMarker);
                    assertNotEquals(ProcessHandle.current().pid(), originalPid);
                    fixture.assertCommittedCut();
                    fixture.assertNoExecutionEvidence();
                    cut = fixture.forkCut();
                    assertEquals(forkMarker("CAPTURE", originalPid, cut.sha256()), acknowledgement);
                    assertTrue(original.isAlive(), "the original must still be parked before budget reservation");
                    original.destroyForcibly();
                    assertTrue(original.waitFor(FORK_CHILD_SECONDS, TimeUnit.SECONDS), "original checkpoint process was not reaped");
                    assertNotEquals(0, original.exitValue());
                } finally {
                    reapForkChild(original);
                }
                fixture.assertCommittedCut();
                fixture.assertNoExecutionEvidence();
                assertEquals(cut.sha256(), fixture.forkCut().sha256());
                NativeForkWitness before = null;
                if (replacementPhase == NATIVE_RESUME) {
                    before = fixture.nativeForkWitness(cut);
                    fixture.expireNativeSessionLease(before);
                } else {
                    fixture.expireForkRunLease(cut);
                }
                String handoffDigest = before == null ? cut.sha256() : before.handoffSha256();
                var continuation = new AgentCheckpointForkHandoff.Request(url, setupRole, runtimeRole, vectorVersion,
                        typed, fixture.query.runId(), replacementPhase, handoffDigest);
                var replacement = startForkChild(continuation, replacementMarker);
                try {
                    assertNotEquals(originalPid, replacement.pid());
                    assertNotEquals(ProcessHandle.current().pid(), replacement.pid());
                    assertTrue(replacement.waitFor(FORK_CHILD_SECONDS, TimeUnit.SECONDS), "checkpoint replacement process did not exit");
                    assertEquals(0, replacement.exitValue(), "checkpoint replacement process did not verify the durable cut");
                    String stage = replacementPhase == NATIVE_RESUME ? "RESUMED" : "RECONCILED";
                    assertEquals(forkMarker(stage, replacement.pid(), handoffDigest), readForkMarker(replacementMarker));
                } finally {
                    reapForkChild(replacement);
                }
                if (before != null) {
                    fixture.assertNativeResumed(before);
                } else {
                    fixture.assertReconciled(cut);
                    afterReconcile.accept(fixture, cut);
                }
            } finally {
                Files.deleteIfExists(replacementMarker);
                Files.deleteIfExists(captureMarker);
                Files.deleteIfExists(directory);
            }
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (!Boolean.getBoolean(FORK_CHILD_SWITCH) || arguments.length != 1) {
            throw new IllegalArgumentException("Disposable checkpoint fork child is disabled");
        }
        var request = AgentCheckpointForkHandoff.receive(System.in);
        var marker = Path.of(arguments[0]);
        var directory = marker.getParent();
        if (!marker.isAbsolute() || directory == null || !directory.equals(directory.toRealPath())
                || !directory.getFileName().toString().startsWith(FORK_DIRECTORY_PREFIX)
                || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS)
                        .equals(PosixFilePermissions.fromString("rwx------"))
                || !request.phase().markerName().equals(marker.getFileName().toString())
                || Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Invalid disposable checkpoint fork marker target");
        }
        var database = openForkDatabase(request);
        var fixture = new RunFixture(database, new AgentLedgerService(database.setup()),
                AgentCheckpointForkHandoff.tenant(request.typed()), request.typed(), Duration.ZERO, request.runId());
        if (request.phase() == CAPTURE) {
            captureForkCut(database, fixture, request.typed(), marker);
            return;
        }
        fixture.assertCommittedCut();
        fixture.assertNoExecutionEvidence();
        var cut = fixture.forkCut();
        if (request.phase() == NATIVE_RESUME) {
            var before = fixture.nativeForkWitness(cut);
            assertEquals(request.cutSha256(), before.handoffSha256());
            resumeNativeFork(database, fixture, before, request.typed(), marker);
            return;
        }
        assertEquals(request.cutSha256(), cut.sha256());
        fixture.assertOnlyDueRun(cut);
        var reconcilerLedger = new AgentLedgerService(database.runtime());
        assertEquals(1, reconcilerLedger.reconcileDue(1));
        fixture.assertReconciled(cut);
        writeForkMarker(marker, "RECONCILED", cut.sha256());
    }

    private static void captureForkCut(Database database, RunFixture fixture, boolean typed, Path marker) {
        fixture.assertEmptySession();
        var runtime = new AgentLedgerService(database.runtime());
        assertEquals(AgentRunStatusEnum.QUEUED, runtime.get(fixture.actor, fixture.query).status());
        var model = new FakeModel();
        var factory = new AgentFlowFactory(new SpringAiModelAdapterFactory(), new AgentWorkflowStrategyFactory(List.of()));
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var validator = new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator()));
            var control = Executors.newScheduledThreadPool(2,
                    task -> Thread.ofPlatform().daemon().name("checkpoint-fork-control").unstarted(task));
            try {
                var cutEstimator = estimator(() -> {
                    assertEquals(0, model.streams.get());
                    assertEquals(0, model.calls.get());
                    fixture.assertCommittedCut();
                    fixture.assertNoExecutionEvidence();
                    var cut = fixture.forkCut();
                    try {
                        writeForkMarker(marker, "CAPTURE", cut.sha256());
                        new CountDownLatch(1).await(FORK_CHILD_SECONDS * 2, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("checkpoint fork was interrupted before forced exit", interrupted);
                    } catch (IOException failure) {
                        throw new AssertionError("checkpoint fork acknowledgement could not be written", failure);
                    }
                    throw new AssertionError("checkpoint fork reached its bound without forced exit");
                });
                var worker = AdkAgentRunExecutor.forRuntime(runtime, factory, validator,
                        Map.of("fixtureModel", model), Map.of(), control, cutEstimator, database.runtime(), typed,
                        fixture::authorize, new DefaultContextAssembler(), new AgentContextRuntimeOptions(false, false), null, true);
                worker.executeQueued(fixture.actor, fixture.query);
                fail("original checkpoint process returned before forced exit");
            } finally {
                control.shutdownNow();
            }
        }
    }

    private static void resumeNativeFork(Database database, RunFixture fixture, NativeForkWitness before,
                                         boolean typed, Path marker) throws IOException {
        var store = new PostgresAdkSessionService(database.runtime(), fixture.actor, APP, false, typed);
        var lease = store.acquireSessionLease(SESSION, before.runLease(), Duration.ofSeconds(30));
        try {
            fixture.assertReplacementSession(before.cut(), true);
            var bound = store.withLease(lease);
            var prior = bound.getSession(APP, bound.adkUserId(), SESSION, Optional.empty())
                    .timeout(5, TimeUnit.SECONDS).blockingGet().immutableEvents();
            assertEquals(1, prior.size());
            String invocation = prior.getFirst().invocationId();
            assertFalse(invocation.isBlank());
            assertEquals(before.prefix().eventId(), prior.getFirst().id());

            var model = new FakeModel(prompt -> {
                store.verifySessionLease(lease);
                assertEquals(1L, prompt.getInstructions().stream().filter(message -> INPUT.equals(message.getText())).count());
            });
            var factory = new AgentFlowFactory(new SpringAiModelAdapterFactory(), new AgentWorkflowStrategyFactory(List.of()));
            var snapshot = new AgentLedgerService(database.runtime()).executionMaterial(fixture.actor, fixture.query).snapshot();
            assertEquals("CONFIG", snapshot.definition().executionMode());
            var config = new ObjectMapper().convertValue(snapshot.definition().configuration(), AgentFlowConfigDTO.class);
            try (var validators = Validation.buildDefaultValidatorFactory()) {
                var validator = new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator()));
                validator.validate(new AgentFlowProperties(true, Duration.ofMinutes(2), Duration.ofSeconds(10),
                        Map.of(APP, config)));
                var root = factory.compileRoot(APP, config, Map.of("fixtureModel", model), List.of());
                var runner = AgentFlowSessionRunner.withExplicitResume(root, APP, bound, List.of());
                try {
                    var output = runner.runAsync(bound.adkUserId(), SESSION, invocation, null, NATIVE_RESUME_CONFIG, null)
                            .timeout(5, TimeUnit.SECONDS).toList().blockingGet();
                    assertFalse(output.isEmpty());
                    assertTrue(output.stream().allMatch(event -> invocation.equals(event.invocationId())));
                    assertTrue(output.stream().anyMatch(AgentCheckpointCapturePostgresTest::hasNativeOutput));
                    assertEquals(1, model.streams.get());
                    assertEquals(0, model.calls.get());
                    var persisted = fixture.nativeRows();
                    assertTrue(persisted.size() > 1);
                    assertEquals(before.prefix(), persisted.getFirst());

                    assertTrue(runner.runAsync(bound.adkUserId(), SESSION, invocation, null, NATIVE_RESUME_CONFIG, null)
                            .timeout(5, TimeUnit.SECONDS).toList().blockingGet().isEmpty());
                    assertEquals(1, model.streams.get());
                    assertEquals(0, model.calls.get());
                    assertTrue(persisted.equals(fixture.nativeRows()), "completed repeat must preserve the persisted native history");
                } finally {
                    runner.close().timeout(5, TimeUnit.SECONDS).blockingAwait();
                }
            }
        } finally {
            assertTrue(store.releaseSessionLease(lease));
        }
        fixture.assertNativeResumed(before);
        writeForkMarker(marker, "RESUMED", before.handoffSha256());
    }

    private static boolean hasNativeOutput(Event event) {
        return event.content().flatMap(content -> content.parts()).orElse(List.of()).stream()
                .anyMatch(part -> part.text().filter(OUTPUT::equals).isPresent());
    }

    private static Database openForkDatabase(AgentCheckpointForkHandoff.Request request) {
        String database = AgentCheckpointForkHandoff.databaseName(request.url());
        var setup = dataSource(request.url(), request.setupRole());
        var observer = new JdbcTemplate(setup);
        observer.setQueryTimeout(5);
        assertForkConnection(observer, database, request.setupRole());
        assertEquals(request.vectorVersion(), observer.queryForObject("""
                SELECT e.extversion FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
                WHERE e.extname='vector' AND n.nspname='public'
                """, String.class));
        var history = observer.queryForList(HISTORY);
        assertEquals(16, history.size());
        assertEquals(Map.of("version", "16", "script", "V16__add_agent_checkpoint.sql", "type", "SQL",
                "checksum", -415542202, "success", true), history.getLast());
        flyway(setup, "16").validate();
        var runtime = dataSource(request.url(), request.runtimeRole());
        assertForkConnection(new JdbcTemplate(runtime), database, request.runtimeRole());
        new AgentStorageVerifier(runtime).verifyCheckpointCapture();
        return new Database(setup, runtime, observer, '"' + request.runtimeRole() + '"');
    }

    private static void assertForkConnection(JdbcTemplate jdbc, String database, String role) {
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_database()=? AND current_user=? AND inet_server_addr()=inet '127.0.0.1'
                  AND NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'
                  AND current_setting('session_replication_role')='origin'
                  AND current_setting('transaction_read_only')='off'
                """, Boolean.class, database, role)));
    }

    private static Process startForkChild(AgentCheckpointForkHandoff.Request request, Path marker) throws IOException {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        if (classpath == null || classpath.isBlank()) throw new IllegalStateException("Missing checkpoint fork test classpath");
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath();
        var builder = new ProcessBuilder(java.toString(), "-D" + FORK_CHILD_SWITCH + "=true",
                "-Dotel.traces.exporter=none", "-Dotel.metrics.exporter=none", "-Dotel.logs.exporter=none",
                "-cp", classpath, AgentCheckpointCapturePostgresTest.class.getName(), marker.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().put("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS", "false");
        Process child = builder.start();
        try {
            try (var pipe = child.getOutputStream()) {
                AgentCheckpointForkHandoff.send(pipe, request);
            }
            return child;
        } catch (IOException | RuntimeException failure) {
            try {
                reapForkChild(child);
            } catch (RuntimeException | AssertionError cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static String awaitForkCapture(Process child, Path marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(FORK_CHILD_SECONDS);
        String prefix = "CAPTURE " + child.pid() + " ";
        while (System.nanoTime() - deadline < 0 && child.isAlive()) {
            String value = readForkMarker(marker);
            if (value.startsWith(prefix) && value.substring(prefix.length()).matches("[0-9a-f]{64}\\n")) return value;
            Thread.sleep(20);
        }
        return fail("original checkpoint process did not acknowledge the durable cut");
    }

    private static String readForkMarker(Path marker) throws IOException {
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) return "";
        try (var stream = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = stream.readNBytes(129);
            if (bytes.length > 128) throw new IllegalStateException("Invalid disposable checkpoint fork marker");
            return new String(bytes, StandardCharsets.US_ASCII);
        }
    }

    private static void writeForkMarker(Path marker, String stage, String digest) throws IOException {
        Files.writeString(marker, forkMarker(stage, ProcessHandle.current().pid(), digest), StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static String forkMarker(String stage, long pid, String digest) {
        return stage + " " + pid + " " + digest + "\n";
    }

    private static void reapForkChild(Process child) {
        if (child.isAlive()) child.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(FORK_CHILD_SECONDS);
        try {
            while (child.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) fail("checkpoint fork child was not reaped");
                try {
                    assertTrue(child.waitFor(remaining, TimeUnit.NANOSECONDS), "checkpoint fork child was not reaped");
                } catch (InterruptedException waitInterrupted) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private record ForkCut(String sha256, long epoch, long revision, long sequence, String leaseOwner,
                           long sessionEpoch, String sessionLeaseOwner) {
        @Override public String toString() { return "ForkCut[redacted]"; }
    }

    private record TakeoverCommitWitness(String leaseSha256, List<AgentEventDTO> events) {
        @Override public String toString() { return "TakeoverCommitWitness[redacted]"; }
    }

    private record NativeForkWitness(ForkCut cut, AgentRunLease runLease, NativeRow prefix, String checkpointSha256) {
        String handoffSha256() {
            String owner = runLease.owner();
            return forkDigest(cut.sha256() + owner.length() + ":" + owner + runLease.expiresAt());
        }

        @Override public String toString() { return "NativeForkWitness[redacted]"; }
    }

    private record NativeRow(long sequence, String eventId, long timestampMillis, String payload) {
        @Override public String toString() { return "NativeRow[redacted]"; }
    }

    private record SessionFence(long epoch, String owner, boolean live, boolean released) {
        @Override public String toString() { return "SessionFence[redacted]"; }
    }

    private static String forkDigest(String witness) {
        byte[] bytes = witness.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16_384) throw new IllegalStateException("Unexpected disposable checkpoint fork witness size");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static Database provision() {
        return provision(null);
    }

    private static Database provision(String readerRole) {
        String url = System.getProperty("tianji.postgres.checkpoint.capture.url", "");
        String role = System.getProperty("tianji.postgres.checkpoint.capture.runtime-role", "");
        String vectorVersion = System.getProperty("tianji.postgres.checkpoint.capture.vector.version", "");
        var match = URL.matcher(url);
        if (!Boolean.getBoolean("tianji.postgres.checkpoint.capture") || !match.matches()
                || Integer.parseInt(match.group(1)) > 65_535 || !role.matches("tianji_it_checkpoint_[a-z0-9_]{1,32}")
                || !vectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || role.equals(readerRole)) {
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
        assertLimitedLogin(observer, role);
        if (readerRole != null) {
            assertLimitedLogin(observer, readerRole);
            var reader = new JdbcTemplate(dataSource(url, readerRole));
            assertTrue(Boolean.TRUE.equals(reader.queryForObject("""
                    SELECT current_database()=? AND current_user=? AND inet_server_addr()=inet '127.0.0.1'
                      AND NOT pg_is_in_recovery()
                    """, Boolean.class, match.group(2), readerRole)));
        }

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
        if (readerRole != null) grantCheckpointReader(observer, readerRole);
        var runtime = dataSource(url, role);
        assertTrue(new JdbcTemplate(runtime).queryForObject("SELECT current_user=?", Boolean.class, role));
        assertDoesNotThrow(new AgentStorageVerifier(runtime)::verifyCheckpointCapture);
        return new Database(setup, runtime, observer, quotedRole);
    }

    private static void assertLimitedLogin(JdbcTemplate observer, String role) {
        assertTrue(Boolean.TRUE.equals(observer.queryForObject("""
                SELECT current_user<>? AND EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname=?
                    AND r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls AND NOT r.rolcreatedb
                    AND NOT r.rolcreaterole AND NOT r.rolreplication
                    AND NOT EXISTS (SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid))
                """, Boolean.class, role, role)));
    }

    private static void grantCheckpointReader(JdbcTemplate observer, String readerRole) {
        String quotedReader = '"' + readerRole + '"';
        observer.execute("GRANT USAGE ON SCHEMA tianji_agent TO " + quotedReader);
        observer.execute("""
                GRANT SELECT ON tianji_agent.flyway_schema_history,tianji_agent.agent_definition_version,
                  tianji_agent.agent_run,tianji_agent.agent_event,tianji_agent.agent_budget_reservation,
                  tianji_agent.agent_session,tianji_agent.agent_session_event,tianji_agent.agent_session_scope_state,
                  tianji_agent.agent_session_lease,tianji_agent.agent_checkpoint TO
                """ + quotedReader);
        observer.execute("GRANT SELECT (tenant_id,run_id) ON tianji_agent.agent_tool_invocation TO " + quotedReader);
        observer.execute("GRANT SELECT (tenant_id,origin_run_id) ON tianji_agent.agent_tool_action_intent TO " + quotedReader);
    }

    private static void grantCheckpointTakeover(JdbcTemplate observer, String quotedRole) {
        observer.execute("GRANT SELECT ON tianji_agent.agent_run_command_receipt TO " + quotedRole);
        observer.execute("""
                GRANT UPDATE (status, wait_reason, revision, last_event_sequence, lease_epoch, lease_owner, lease_expires_at)
                  ON tianji_agent.agent_run TO
                """ + quotedRole);
        // SELECT FOR UPDATE needs UPDATE privilege on at least one column of this locked relation.
        observer.execute("GRANT UPDATE (revision) ON tianji_agent.agent_session TO " + quotedRole);
        observer.execute("""
                GRANT UPDATE (lease_epoch, lease_owner, lease_expires_at) ON tianji_agent.agent_session_lease TO
                """ + quotedRole);
        observer.execute("""
                GRANT INSERT (tenant_id, run_id, sequence, event_id, event_type, schema_version, correlation_id, payload, occurred_at)
                  ON tianji_agent.agent_event TO
                """ + quotedRole);
        observer.execute("""
                GRANT INSERT (tenant_id, subject_type, subject_issuer, subject_id, command_id, operation, request_hash, run_id, created_at),
                  UPDATE (result) ON tianji_agent.agent_run_command_receipt TO
                """ + quotedRole);
    }

    private static void assertCheckpointReaderPrivileges(JdbcTemplate reader) {
        assertTrue(Boolean.TRUE.equals(reader.queryForObject("""
                SELECT NOT has_schema_privilege('tianji_agent','CREATE')
                  AND NOT has_table_privilege('tianji_agent.agent_tool_invocation','SELECT')
                  AND NOT has_table_privilege('tianji_agent.agent_tool_action_intent','SELECT')
                  AND NOT EXISTS (
                      SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
                      WHERE n.nspname='tianji_agent' AND c.relkind='r'
                        AND (pg_has_role(c.relowner,'MEMBER')
                             OR has_table_privilege(c.oid,'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')
                             OR has_any_column_privilege(c.oid,'INSERT,UPDATE,REFERENCES,SELECT WITH GRANT OPTION'))
                  )
                """, Boolean.class)));
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

    private static AgentDefinitionSpecDTO definition(Duration deadline) {
        var dependencies = new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), ROUTE, POLICY);
        var policy = new ExecutionPolicyDTO(deadline, Duration.ofSeconds(8), Duration.ofSeconds(2),
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
            this(database, setupLedger, tenant, typed, Duration.ofSeconds(35), null);
        }

        RunFixture(Database database, AgentLedgerService setupLedger, long tenant, boolean typed,
                   Duration deadline, String existingRun) {
            this.database = database;
            this.actor = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.USER,
                    "checkpoint-fixture-owner", "https://checkpoint-fixture.example");
            this.codec = typed ? 2 : 1;
            this.sessions = new PostgresAdkSessionService(database.setup(), actor, APP, false, typed);
            if (existingRun == null) {
                assertNotNull(sessions.createSession(APP, sessions.adkUserId(), Map.of(), SESSION)
                        .timeout(5, TimeUnit.SECONDS).blockingGet());
                setupLedger.publish(actor, definition(deadline));
                var run = setupLedger.start(actor, new StartAgentRunCommand(DEFINITION, 1, SESSION, "first-input",
                        null, null, new AgentInputDTO(INPUT, List.of(), Map.of()), null));
                this.query = new GetAgentRunQuery(run.runId());
                assertEmptySession();
            } else {
                this.query = new GetAgentRunQuery(existingRun);
            }
            this.authorization = setupLedger.executionMaterial(actor, query).authorization();
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

        ForkCut forkCut() {
            return database.observer().queryForObject("""
                    SELECT jsonb_build_array(to_jsonb(c)-'captured_at',extract(epoch FROM c.captured_at),
                             s.session_state,s.state_codec_version,s.revision,s.last_event_sequence,
                             to_jsonb(e),l.lease_epoch,l.lease_owner)::text AS cut,
                           r.lease_epoch,r.revision,r.last_event_sequence,r.lease_owner,
                           l.lease_epoch AS session_lease_epoch,l.lease_owner AS session_lease_owner
                    FROM tianji_agent.agent_checkpoint c
                    JOIN tianji_agent.agent_run r ON
                      (r.tenant_id,r.run_id,r.subject_type,r.subject_issuer,r.subject_id,r.session_id)=
                      (c.tenant_id,c.run_id,c.subject_type,c.subject_issuer,c.subject_id,c.session_id)
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
                      AND c.app_name=? AND c.session_id=? AND c.checkpoint_no=1 AND c.attempt=1
                      AND c.native_event_id=e.native_event_id AND l.lease_owner IS NOT NULL
                    """, (row, index) -> new ForkCut(forkDigest(row.getString("cut")), row.getLong("lease_epoch"),
                    row.getLong("revision"), row.getLong("last_event_sequence"), row.getString("lease_owner"),
                    row.getLong("session_lease_epoch"), row.getString("session_lease_owner")),
                    actor.tenantId(), query.runId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), APP, SESSION);
        }

        void expireForkRunLease(ForkCut cut) {
            assertNotNull(cut.leaseOwner());
            assertEquals(1, database.observer().update("""
                    UPDATE tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second'
                    WHERE tenant_id=? AND run_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND session_id=?
                      AND status='RUNNING' AND attempt=1 AND revision=? AND last_event_sequence=?
                      AND lease_epoch=? AND lease_owner=?
                      AND lease_expires_at>clock_timestamp() AND deadline_at>clock_timestamp()
                    """, actor.tenantId(), query.runId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    SESSION, cut.revision(), cut.sequence(), cut.epoch(), cut.leaseOwner()));
        }

        NativeForkWitness nativeForkWitness(ForkCut cut) {
            var prefix = nativeRows();
            assertEquals(1, prefix.size());
            assertEquals(1, prefix.getFirst().sequence());
            return new NativeForkWitness(cut, liveForkRunLease(cut), prefix.getFirst(), checkpointDigest());
        }

        private AgentRunLease liveForkRunLease(ForkCut cut) {
            return database.observer().queryForObject("""
                    SELECT lease_expires_at FROM tianji_agent.agent_run
                    WHERE tenant_id=? AND run_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND session_id=?
                      AND status='RUNNING' AND attempt=1 AND revision=? AND last_event_sequence=? AND lease_epoch=?
                      AND lease_owner=? AND lease_expires_at>clock_timestamp() AND deadline_at>clock_timestamp()
                      AND cancel_requested_at IS NULL AND result IS NULL AND finished_at IS NULL
                    """, (row, index) -> new AgentRunLease(query.runId(), cut.leaseOwner(), cut.epoch(),
                    row.getTimestamp("lease_expires_at").toInstant()),
                    actor.tenantId(), query.runId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    SESSION, cut.revision(), cut.sequence(), cut.epoch(), cut.leaseOwner());
        }

        void expireNativeSessionLease(NativeForkWitness before) {
            var cut = before.cut();
            assertNotNull(cut.sessionLeaseOwner());
            assertEquals(1, database.observer().update("""
                    UPDATE tianji_agent.agent_session_lease l SET lease_expires_at=clock_timestamp()-interval '1 second'
                    FROM tianji_agent.agent_checkpoint c JOIN tianji_agent.agent_run r
                      ON (r.tenant_id,r.run_id,r.subject_type,r.subject_issuer,r.subject_id,r.session_id)=
                         (c.tenant_id,c.run_id,c.subject_type,c.subject_issuer,c.subject_id,c.session_id)
                    WHERE (l.tenant_id,l.subject_type,l.subject_issuer,l.subject_id,l.app_name,l.session_id)=
                          (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id)
                      AND c.tenant_id=? AND c.run_id=? AND c.subject_type=? AND c.subject_issuer=? AND c.subject_id=?
                      AND c.app_name=? AND c.session_id=? AND c.attempt=1 AND c.checkpoint_no=1
                      AND r.status='RUNNING' AND r.attempt=1 AND r.lease_epoch=? AND r.lease_owner=?
                      AND r.revision=? AND r.last_event_sequence=? AND r.lease_expires_at=?
                      AND r.lease_expires_at>clock_timestamp() AND r.deadline_at>clock_timestamp()
                      AND r.cancel_requested_at IS NULL AND r.result IS NULL AND r.finished_at IS NULL
                      AND l.lease_epoch=c.session_lease_epoch AND l.lease_epoch=? AND l.lease_owner=?
                      AND l.lease_expires_at>clock_timestamp()
                    """, actor.tenantId(), query.runId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    APP, SESSION, cut.epoch(), cut.leaseOwner(), cut.revision(), cut.sequence(),
                    java.sql.Timestamp.from(before.runLease().expiresAt()), cut.sessionEpoch(), cut.sessionLeaseOwner()));
        }

        void assertReplacementSession(ForkCut original, boolean active) {
            var fence = database.observer().queryForObject("""
                    SELECT lease_epoch, lease_owner,
                           lease_expires_at>clock_timestamp() AS live, lease_expires_at IS NULL AS released
                    FROM tianji_agent.agent_session_lease
                    """ + SESSION_SCOPE, (row, index) -> new SessionFence(row.getLong("lease_epoch"),
                    row.getString("lease_owner"), row.getBoolean("live"), row.getBoolean("released")), scope());
            assertNotNull(fence);
            assertEquals(original.sessionEpoch() + 1, fence.epoch());
            if (active) {
                assertNotNull(fence.owner());
                assertTrue(!original.sessionLeaseOwner().equals(fence.owner()), "the successor must own a distinct Session lease");
                assertTrue(fence.live());
            } else {
                assertTrue(fence.owner() == null && fence.released(), "the successor must release its Session lease");
            }
        }

        List<NativeRow> nativeRows() {
            return database.observer().query("""
                    SELECT sequence,native_event_id,event_timestamp_ms,payload FROM tianji_agent.agent_session_event
                    """ + SESSION_SCOPE + " ORDER BY sequence LIMIT 9", (row, index) -> new NativeRow(row.getLong("sequence"),
                    row.getString("native_event_id"), row.getLong("event_timestamp_ms"), row.getString("payload")), scope());
        }

        private String checkpointDigest() {
            String witness = database.observer().queryForObject("""
                    SELECT jsonb_build_array(to_jsonb(c)-'captured_at',extract(epoch FROM c.captured_at))::text
                    FROM tianji_agent.agent_checkpoint c
                    WHERE c.tenant_id=? AND c.run_id=? AND c.subject_type=? AND c.subject_issuer=? AND c.subject_id=?
                      AND c.app_name=? AND c.session_id=? AND c.attempt=1 AND c.checkpoint_no=1
                    """, String.class, actor.tenantId(), query.runId(), actor.subjectType().name(), actor.issuerFingerprint(),
                    actor.subjectId(), APP, SESSION);
            return forkDigest(witness);
        }

        void assertNativeResumed(NativeForkWitness before) {
            assertTrue(before.runLease().equals(liveForkRunLease(before.cut())), "the original Run lease must stay live and unchanged");
            assertReplacementSession(before.cut(), false);
            assertEquals(1, checkpoints());
            assertEquals(before.checkpointSha256(), checkpointDigest());
            var rows = nativeRows();
            assertTrue(rows.size() > 1 && rows.size() <= 8, "native history must have bounded continuation");
            assertEquals(before.prefix(), rows.getFirst());

            var session = sessions.getSession(APP, sessions.adkUserId(), SESSION, Optional.empty())
                    .timeout(5, TimeUnit.SECONDS).blockingGet();
            assertNotNull(session);
            var history = session.immutableEvents();
            assertEquals(rows.size(), history.size());
            var original = history.getFirst();
            assertEquals(before.prefix().eventId(), original.id());
            assertEquals("user", original.author());
            assertEquals(1L, history.stream().filter(event -> "user".equals(event.author())).count());
            assertTrue(history.stream().allMatch(event -> original.invocationId().equals(event.invocationId())));
            assertTrue(history.stream().anyMatch(AgentCheckpointCapturePostgresTest::hasNativeOutput));
            assertTrue(history.stream().anyMatch(event -> "writer".equals(event.author()) && event.actions().endOfAgent()));
            assertNoExecutionEvidence();
            assertEquals(0, database.observer().queryForObject("""
                    SELECT count(*) FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND origin_run_id=?
                    """, Integer.class, actor.tenantId(), query.runId()));
        }

        void assertOnlyDueRun(ForkCut cut) {
            assertEquals(List.of(query.runId()), database.observer().queryForList("""
                    SELECT run_id FROM tianji_agent.agent_run
                    WHERE status IN ('QUEUED','RUNNING','WAITING','SUSPENDED')
                      AND (deadline_at<=clock_timestamp()
                           OR (status='RUNNING' AND (lease_expires_at IS NULL OR lease_expires_at<=clock_timestamp())))
                    ORDER BY deadline_at,created_at,run_id LIMIT 2
                    """, String.class));
            assertTrue(Boolean.TRUE.equals(database.observer().queryForObject("""
                    SELECT lease_expires_at<=clock_timestamp() AND deadline_at>clock_timestamp()
                    FROM tianji_agent.agent_run
                    WHERE tenant_id=? AND run_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?
                      AND lease_owner=? AND lease_epoch=? AND status='RUNNING'
                    """, Boolean.class, actor.tenantId(), query.runId(), actor.subjectType().name(), actor.issuerFingerprint(),
                    actor.subjectId(), cut.leaseOwner(), cut.epoch())));
        }

        void assertNoExecutionEvidence() {
            assertEquals(0, reservations());
            assertEquals(0, database.observer().queryForObject("""
                    SELECT count(*) FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=?
                    """, Integer.class, actor.tenantId(), query.runId()));
            assertTrue(Boolean.TRUE.equals(database.observer().queryForObject("""
                    SELECT model_calls_reserved=0 AND tool_calls_reserved=0
                      AND active_model_calls=0 AND active_tool_calls=0 AND result IS NULL AND finished_at IS NULL
                    FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?
                    """, Boolean.class, actor.tenantId(), query.runId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId())));
        }

        void assertReconciled(ForkCut original) {
            var ledger = new AgentLedgerService(database.setup());
            var run = ledger.get(actor, query);
            assertEquals(AgentRunStatusEnum.SUSPENDED, run.status());
            assertEquals("LEASE_EXPIRED", run.waitReason());
            assertEquals(1, run.attempt());
            assertEquals(original.revision() + 1, run.revision());
            assertEquals(original.sequence() + 1, run.lastEventSequence());
            var events = ledger.events(actor, new AgentEventsQuery(query.runId(), original.sequence(), 2));
            assertEquals(1, events.size());
            var event = events.getFirst();
            assertEquals(run.lastEventSequence(), event.sequence());
            assertEquals("RUN_STATUS_CHANGED", event.type());
            assertEquals(Map.of("from", "RUNNING", "status", "SUSPENDED", "reason", "LEASE_EXPIRED"), event.payload());
            assertTrue(ledger.getResult(actor, query).isEmpty());
            var cut = forkCut();
            assertEquals(original.sha256(), cut.sha256());
            assertEquals(original.epoch() + 1, cut.epoch());
            assertNull(cut.leaseOwner());
            assertEquals(1, checkpoints());
            assertEquals(1, database.observer().queryForObject(
                    "SELECT count(*) FROM tianji_agent.agent_session_event " + SESSION_SCOPE, Integer.class, scope()));
            assertNoExecutionEvidence();
        }

        void assertCheckpointObservation(ForkCut cut, AgentCheckpointObservationService service,
                                         JdbcTemplate reader, String quotedReader) {
            expireReconciledSessionLease(cut);
            assertEquals(Observation.BOUNDARY_OBSERVED, service.observe(actor, query));

            var otherOwner = TrustedAgentActor.verified(actor.tenantId(), TrustedAgentActor.SubjectType.USER,
                    "checkpoint-other-owner", "https://checkpoint-fixture.example");
            var hidden = assertThrows(AgentLedgerException.class, () -> service.observe(otherOwner, query));
            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, hidden.code());

            database.observer().execute("REVOKE SELECT (run_id) ON tianji_agent.agent_tool_invocation FROM " + quotedReader);
            try {
                assertEquals(Boolean.FALSE, reader.queryForObject("""
                        SELECT has_column_privilege('tianji_agent.agent_tool_invocation','run_id','SELECT')
                        """, Boolean.class));
                assertEquals(Observation.TOOL_EVIDENCE_UNAVAILABLE, service.observe(actor, query));
            } finally {
                database.observer().execute("GRANT SELECT (run_id) ON tianji_agent.agent_tool_invocation TO " + quotedReader);
            }
            assertEquals(Observation.BOUNDARY_OBSERVED, service.observe(actor, query));

            changeObservationSessionRevision(1, 2);
            try {
                assertEquals(Observation.NOT_AT_CAPTURE_FENCE, service.observe(actor, query));
            } finally {
                changeObservationSessionRevision(2, 1);
            }
            assertEquals(Observation.BOUNDARY_OBSERVED, service.observe(actor, query));
            assertReconciled(cut);
        }

        private void expireReconciledSessionLease(ForkCut cut) {
            assertNotNull(cut.sessionLeaseOwner());
            assertEquals(1, database.observer().update("""
                    UPDATE tianji_agent.agent_session_lease l SET lease_expires_at=clock_timestamp()-interval '1 second'
                    FROM tianji_agent.agent_checkpoint c JOIN tianji_agent.agent_run r
                      ON (r.tenant_id,r.run_id,r.subject_type,r.subject_issuer,r.subject_id,r.session_id)=
                         (c.tenant_id,c.run_id,c.subject_type,c.subject_issuer,c.subject_id,c.session_id)
                    WHERE (l.tenant_id,l.subject_type,l.subject_issuer,l.subject_id,l.app_name,l.session_id)=
                          (c.tenant_id,c.subject_type,c.subject_issuer,c.subject_id,c.app_name,c.session_id)
                      AND c.tenant_id=? AND c.run_id=? AND c.subject_type=? AND c.subject_issuer=? AND c.subject_id=?
                      AND c.app_name=? AND c.session_id=? AND c.attempt=1 AND c.checkpoint_no=1
                      AND r.status='SUSPENDED' AND r.wait_reason='LEASE_EXPIRED' AND r.lease_epoch=? AND r.revision=?
                      AND l.lease_epoch=c.session_lease_epoch AND l.lease_epoch=? AND l.lease_owner=?
                      AND l.lease_expires_at IS NOT NULL
                    """, actor.tenantId(), query.runId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    APP, SESSION, cut.epoch() + 1, cut.revision() + 1, cut.sessionEpoch(), cut.sessionLeaseOwner()));
        }

        private void changeObservationSessionRevision(long previous, long replacement) {
            assertEquals(1, database.observer().update("""
                    UPDATE tianji_agent.agent_session SET revision=?
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name=? AND session_id=?
                      AND revision=? AND last_event_sequence=1
                    """, replacement, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    APP, SESSION, previous));
        }

        void assertCheckpointTakeover(ForkCut cut, DriverManagerDataSource takeoverSource) {
            expireReconciledSessionLease(cut);
            assertReconciled(cut);
            var ledger = new AgentLedgerService(database.setup());
            var suspended = ledger.get(actor, query);
            var prefix = nativeRows();
            assertEquals(1, prefix.size());
            String checkpoint = checkpointDigest();
            var command = new ResumeCommand(query.runId(), "checkpoint-takeover", suspended.revision());
            var expired = new ResumeCommand(query.runId(), "checkpoint-takeover-expired", suspended.revision());
            var decisions = new AtomicInteger();
            var allowedUntil = new AtomicReference<Instant>();
            var takeover = new AgentCheckpointTakeoverService(takeoverSource, request -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                        "the trusted authority port must be called outside the claim transaction");
                assertEquals(authorization, request.execution());
                assertTrue(request.command().equals(command) || request.command().equals(expired),
                        "only the exact expected commands may reach fixture authority");
                assertTrue(request.cutSha256().matches("[0-9a-f]{64}"));
                decisions.incrementAndGet();
                Instant now = database.observer().queryForObject("SELECT clock_timestamp()",
                        (row, index) -> row.getTimestamp(1).toInstant());
                assertNotNull(now);
                if (request.command().equals(expired)) {
                    return Optional.of(new ResumePermit(request, now.minusSeconds(1)));
                }
                Instant expiry = now.plusSeconds(20);
                allowedUntil.set(expiry);
                return Optional.of(new ResumePermit(request, expiry));
            });

            var wrongOwner = TrustedAgentActor.verified(actor.tenantId(), TrustedAgentActor.SubjectType.USER,
                    "checkpoint-other-owner", "https://checkpoint-fixture.example");
            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                    assertThrows(AgentLedgerException.class, () -> takeover.takeOver(wrongOwner, command, "checkpoint-worker")).code());
            var stale = new ResumeCommand(query.runId(), "checkpoint-takeover-stale", suspended.revision() - 1);
            assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                    assertThrows(AgentLedgerException.class, () -> takeover.takeOver(actor, stale, "checkpoint-worker")).code());
            assertEquals(0, decisions.get());
            assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                    assertThrows(AgentLedgerException.class, () -> takeover.takeOver(actor, expired, "checkpoint-worker")).code());
            assertEquals(1, decisions.get());
            assertEquals(0, resumeReceipts());
            assertReconciled(cut);

            String worker = "checkpoint-worker";
            var expected = new Acknowledgement(query.runId(), AgentRunStatusEnum.RUNNING, 1, suspended.revision() + 2);
            assertEquals(expected, takeover.takeOver(actor, command, worker).acknowledgement());
            assertEquals(2, decisions.get());
            assertNotNull(allowedUntil.get());
            var committed = assertTakeoverCommitted(cut, suspended.lastEventSequence(), expected, worker, allowedUntil.get());
            String receipt = resumeReceiptDigest(command, expected);
            assertEquals(expected, takeover.takeOver(actor, command, "checkpoint-replay-worker").acknowledgement());
            var reused = new ResumeCommand(query.runId(), command.commandId(), command.expectedRevision() + 1);
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                    assertThrows(AgentLedgerException.class, () -> takeover.takeOver(actor, reused, worker)).code());
            assertEquals(2, decisions.get());
            assertEquals(committed, assertTakeoverCommitted(cut, suspended.lastEventSequence(), expected, worker, allowedUntil.get()));
            assertEquals(receipt, resumeReceiptDigest(command, expected));
            assertEquals(1, resumeReceipts());
            assertEquals(1, checkpoints());
            assertEquals(checkpoint, checkpointDigest());
            assertEquals(prefix, nativeRows());
            assertNoExecutionEvidence();
            assertEquals(0, database.observer().queryForObject("""
                    SELECT count(*) FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND origin_run_id=?
                    """, Integer.class, actor.tenantId(), query.runId()));
        }

        private int resumeReceipts() {
            return database.observer().queryForObject("""
                    SELECT count(*) FROM tianji_agent.agent_run_command_receipt
                    WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=? AND operation='RESUME'
                    """, Integer.class, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(),
                    actor.subjectId(), query.runId());
        }

        private String resumeReceiptDigest(ResumeCommand command, Acknowledgement expected) {
            String receipt = database.observer().queryForObject("""
                    SELECT pg_catalog.jsonb_build_array(pg_catalog.to_jsonb(c)-'created_at',
                               extract(epoch FROM c.created_at))::text
                    FROM tianji_agent.agent_run_command_receipt c
                    WHERE c.tenant_id=? AND c.subject_type=? AND c.subject_issuer=? AND c.subject_id=?
                      AND c.operation='RESUME' AND c.run_id=? AND c.command_id=?
                      AND c.result=pg_catalog.jsonb_build_object('runId',c.run_id,'status','RUNNING','attempt',1,'revision',CAST(? AS bigint))
                    """, String.class, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(),
                    actor.subjectId(), query.runId(), command.commandId(), expected.revision());
            return forkDigest(receipt);
        }

        private TakeoverCommitWitness assertTakeoverCommitted(ForkCut cut, long suspendedSequence, Acknowledgement expected,
                                                              String worker, Instant permitExpiry) {
            var ledger = new AgentLedgerService(database.setup());
            var run = ledger.get(actor, query);
            assertEquals(expected.runId(), run.runId());
            assertEquals(expected.status(), run.status());
            assertEquals(expected.attempt(), run.attempt());
            assertEquals(expected.revision(), run.revision());
            assertEquals(suspendedSequence + 2, run.lastEventSequence());
            String lease = database.observer().queryForObject("""
                    SELECT CASE WHEN r.status='RUNNING' AND r.wait_reason IS NULL
                      AND r.lease_epoch=? AND r.lease_owner=? AND r.lease_expires_at>pg_catalog.clock_timestamp()
                      AND r.lease_expires_at<=? AND r.lease_expires_at<=r.deadline_at
                      AND l.lease_epoch=? AND l.lease_owner IS NOT NULL AND l.lease_owner<>?
                      AND l.lease_expires_at=r.lease_expires_at
                      THEN pg_catalog.jsonb_build_array(r.lease_epoch,r.lease_owner,extract(epoch FROM r.lease_expires_at),
                               l.lease_epoch,l.lease_owner,extract(epoch FROM l.lease_expires_at))::text END
                    FROM tianji_agent.agent_run r JOIN tianji_agent.agent_session_lease l
                      ON (r.tenant_id,r.subject_type,r.subject_issuer,r.subject_id,r.session_id)=
                         (l.tenant_id,l.subject_type,l.subject_issuer,l.subject_id,l.session_id)
                    WHERE r.tenant_id=? AND r.run_id=? AND r.subject_type=? AND r.subject_issuer=? AND r.subject_id=?
                      AND l.app_name=? AND l.session_id=?
                    """, String.class, cut.epoch() + 2, worker, java.sql.Timestamp.from(permitExpiry),
                    cut.sessionEpoch() + 1, cut.sessionLeaseOwner(), actor.tenantId(), query.runId(),
                    actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), APP, SESSION);
            assertNotNull(lease, "the committed Run and Session leases must be live, exact and bounded by authority");
            var events = ledger.events(actor, new AgentEventsQuery(query.runId(), suspendedSequence, 3));
            assertEquals(2, events.size());
            assertEquals(suspendedSequence + 1, events.getFirst().sequence());
            assertEquals(suspendedSequence + 2, events.getLast().sequence());
            assertTrue(events.stream().allMatch(event -> "RUN_STATUS_CHANGED".equals(event.type())));
            assertEquals(Map.of("from", "SUSPENDED", "status", "QUEUED", "reason", "CHECKPOINT_RESUME"), events.getFirst().payload());
            assertEquals(Map.of("from", "QUEUED", "status", "RUNNING", "reason", "CHECKPOINT_RESUME"), events.getLast().payload());
            assertTrue(ledger.getResult(actor, query).isEmpty());
            return new TakeoverCommitWitness(forkDigest(lease), events);
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
        private final Consumer<Prompt> beforeStream;

        FakeModel() { this(prompt -> { }); }

        FakeModel(Consumer<Prompt> beforeStream) { this.beforeStream = beforeStream; }

        @Override public ChatOptions getOptions() { return options; }

        @Override public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            throw new AssertionError("checkpoint probe only uses the bounded fake stream");
        }

        @Override public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                beforeStream.accept(prompt);
                streams.incrementAndGet();
                return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(OUTPUT),
                        ChatGenerationMetadata.builder().finishReason("stop").build()))));
            });
        }
    }
}
