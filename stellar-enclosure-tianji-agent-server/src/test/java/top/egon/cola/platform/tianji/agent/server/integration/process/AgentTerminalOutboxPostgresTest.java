package top.egon.cola.platform.tianji.agent.server.integration.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.OutboxJdbcAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.OutboxStateMachineAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxAutoConfiguration;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.outbox.store.OutboxStore;
import top.egon.cola.component.outbox.transaction.OutboxAfterCommitBuffer;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO.DecisionEnum;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactQuery.PurposeEnum;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactVerifier;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianji.client.HttpAgentResultClient;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentResultArtifactRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.RuntimeProfileEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateConfigurationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateDependencyRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real JDBC Outbox/ledger test with a controlled receiver client, only in a supplied fresh loopback database. */
@Timeout(90)
class AgentTerminalOutboxPostgresTest {
    @Test
    @EnabledIfSystemProperty(named = "tianji.agent.outbox.runtime-v2.postgres.test.url", matches = ".+")
    void runtime_terminal_completion_and_close_rollback_with_real_outbox_then_publish_exactly_once() throws Exception {
        var fixture = provisionRuntime();
        var source = fixture.source();
        var admin = fixture.admin();
        var jdbc = new JdbcTemplate(source);
        var manager = new DataSourceTransactionManager(source);
        var tx = new TransactionTemplate(manager);
        var owner = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "process-worker", "https://idp.example/20");
        var ledgerReference = new AtomicReference<AgentLedgerService>();
        var client = mock(HttpAgentResultClient.class);
        when(client.timeout()).thenReturn(Duration.ofSeconds(2));
        var originalArtifact = new AgentResultArtifactRefVO("fixed-artifact", "a".repeat(64), "application/json", 42);
        var permission = new AtomicReference<>(DecisionEnum.ALLOW);
        var preparations = new AtomicInteger();
        var disclosures = new AtomicInteger();
        var verifier = AgentResultArtifactVerifier.guarded(request -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(owner, request.getOwner());
            assertEquals(originalArtifact, request.getArtifactRef());
            assertEquals("process-east", request.getSourceId());
            assertEquals("agent-east", request.getProducerId());
            assertEquals("process-one", request.getDestinationId());
            if (request.getPurpose() == PurposeEnum.PREPARE_TERMINAL) preparations.incrementAndGet();
            if (request.getPurpose() == PurposeEnum.DELIVER_TERMINAL) disclosures.incrementAndGet();
            if (permission.get() != DecisionEnum.ALLOW) return new AgentResultArtifactEvidenceVO(permission.get(), null, null, null, null);
            return new AgentResultArtifactEvidenceVO(DecisionEnum.ALLOW, "fixture", request.querySha256(),
                    Instant.now().truncatedTo(ChronoUnit.MICROS), request.getDeadlineAt());
        }, Clock.systemUTC());
        var handler = new AgentResultDeliveryHandler(ledgerReference::get,
                List.of(new AgentResultDeliveryHandler.Route("process-one", "process-east", owner, client)),
                Clock.systemUTC(), AgentResultSpanObservation.disabled(), false, verifier);
        try (var cleanup = (AutoCloseable) verifier) {
            outboxContext(source, manager, handler).run(context -> assertDoesNotThrow(() -> {
                assertNull(context.getStartupFailure());
                var publisher = new OutboxAgentTerminalEventPublisher(source, context.getBean(OutboxInfrastructure.class),
                        context.getBean(TransactionalOutbox.class), "agent-east", Map.of("process-east", "process-one"),
                        AgentResultSpanObservation.disabled(), verifier, handler::hasRuntimeRoute);
                var ledger = AgentLedgerService.withTerminalEvents(source, publisher);
                ledgerReference.set(ledger);
                var linked = new AgentLinkedSubmissionService(source, ledger);
                var template = runtimeTemplate();
                ledger.publishRuntimeTemplate(owner, template, () -> true, (actor, request) -> deadline -> true);
                var command = runtimeCommand(ledger, template, "complete");
                var run = linked.submitRuntimeV2(owner, command, () -> true);
                var query = new GetAgentRunQuery(run.runId());
                var claim = ledger.claimRuntimeV2(owner, query, run.revision(), "worker-one", Duration.ofSeconds(30));
                var output = new AgentExecutionResult(run.runId(), AgentRunStatusEnum.COMPLETED, Map.of("value", "中文"),
                        null, null, null, null, "NONE");
                assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
                    ledger.finish(owner, claim.lease(), claim.run().revision(), output);
                    // Observe both writes under the outer transaction before forcing rollback.
                    assertEquals(1, outboxCount(jdbc));
                    assertEquals(AgentRunStatusEnum.COMPLETED, ledger.get(owner, query).status());
                    throw new IllegalStateException("controlled rollback");
            }));
            assertEquals(AgentRunStatusEnum.RUNNING, ledger.get(owner, query).status());
            assertEquals(0, outboxCount(jdbc));
            assertEquals(AgentRunStatusEnum.COMPLETED, ledger.finish(owner, claim.lease(), claim.run().revision(), output).status());
            var event = ledger.terminalReferenceV2(owner, query, Duration.ofSeconds(5)).orElseThrow();
            assertTrue(ledger.terminalReference(owner, query, Duration.ofSeconds(5)).isEmpty());
            assertEquals(Map.of("value", "中文"), event.output());
            assertEquals(Map.of("value", "中文"), ledger.getResult(owner, query).orElseThrow().output());
            assertNull(event.artifactRef());
            assertNull(event.errorCode());
            assertEquals(ProcessTypedJson.sha256("AUTOMATION_NODE_INPUT_V2", command.getInputSchemaSha256().getBytes(StandardCharsets.UTF_8),
                    ProcessTypedJson.canonicalBytes(command.getInput())), event.inputSha256());
            var store = context.getBean(OutboxStore.class);
            var message = store.claimDue(1, "runtime-terminal-worker", Duration.ofSeconds(30)).getFirst();
            byte[] bytes = message.payload().getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(event.canonicalBytes(), bytes);
            assertEquals("2", message.schemaVersion());
            assertEquals(Map.of("tianji-event-fingerprint", event.fingerprint()), message.headers());
            when(client.receiveV2(eq(event), any(byte[].class), isNull())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertArrayEquals(bytes, call.getArgument(1));
                return new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED,
                        Instant.now().truncatedTo(ChronoUnit.MICROS));
            });
            var delivery = new DeliveryContext(message.messageId(), message.channel(), message.destination(), message.payload(),
                    message.contentType(), message.schemaVersion(), message.headers(), message.traceId(), message.attemptCount(),
                    message.maxAttempts(), Instant.now().plusSeconds(10));
            assertEquals(DeliveryResult.Kind.SUCCESS, handler.deliver(delivery).kind());
            assertTrue(store.markSucceeded(message.id(), "runtime-terminal-worker", 0));
            verify(client).receiveV2(eq(event), any(byte[].class), isNull());

            var cancel = runtimeCommand(ledger, template, "cancel");
            var waiting = linked.submitRuntimeV2(owner, cancel, () -> true);
            var close = new LinkedAgentLookupQuery(cancel.getIdempotencyKey(), cancel.getLink());
            admin.execute("""
                    CREATE FUNCTION tianji_agent.test_v2_terminal_outbox_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'controlled Runtime Outbox insert failure'; END $$
                    """);
            admin.execute("CREATE TRIGGER test_v2_terminal_outbox_failure BEFORE INSERT ON tianji_agent.egon_cola_outbox_message "
                    + "FOR EACH ROW EXECUTE FUNCTION tianji_agent.test_v2_terminal_outbox_failure()");
            assertThrows(RuntimeException.class, () -> linked.close(owner, close));
            assertFalse(linked.resolve(owner, close).orElseThrow().closed());
            assertEquals(AgentRunStatusEnum.QUEUED, ledger.get(owner, new GetAgentRunQuery(waiting.runId())).status());
            assertEquals(1, outboxCount(jdbc));
            admin.execute("DROP TRIGGER test_v2_terminal_outbox_failure ON tianji_agent.egon_cola_outbox_message");
            assertEquals(AgentRunStatusEnum.CANCELLED, linked.close(owner, close).run().status());
            assertEquals(AgentRunStatusEnum.CANCELLED, linked.close(owner, close).run().status());
            var cancelled = ledger.terminalReferenceV2(owner, new GetAgentRunQuery(waiting.runId()), Duration.ofSeconds(5)).orElseThrow();
            assertNull(cancelled.output());
            assertNull(cancelled.artifactRef());
            assertNotNull(cancelled.errorCode());
            assertEquals(2, outboxCount(jdbc));
            var cancellationMessage = store.claimDue(1, "runtime-terminal-worker", Duration.ofSeconds(30)).getFirst();
            assertEquals(cancelled.eventId(), cancellationMessage.messageId());
            assertTrue(store.markSucceeded(cancellationMessage.id(), "runtime-terminal-worker", 0));

            var artifactTemplate = runtimeArtifactTemplate();
            ledger.publishRuntimeTemplate(owner, artifactTemplate, () -> true, (actor, request) -> deadline -> true);
            var artifactCommand = runtimeCommand(ledger, artifactTemplate, "artifact");
            var pendingArtifact = linked.submitRuntimeV2(owner, artifactCommand, () -> true);
            var artifactQuery = new GetAgentRunQuery(pendingArtifact.runId());
            var artifactClaim = ledger.claimRuntimeV2(owner, artifactQuery, pendingArtifact.revision(), "worker-one", Duration.ofSeconds(30));
            var artifactOutput = new AgentExecutionResult(pendingArtifact.runId(), AgentRunStatusEnum.COMPLETED, originalArtifact.toWire(),
                    null, null, null, null, "CONFIRMED");
            assertEquals(AgentRunStatusEnum.COMPLETED,
                    ledger.finishRuntimeArtifact(owner, artifactClaim.lease(), artifactClaim.run().revision(), artifactOutput).status());
            var artifactEvent = ledger.terminalReferenceV2(owner, artifactQuery, Duration.ofSeconds(5)).orElseThrow();
            assertNull(artifactEvent.output());
            assertEquals(originalArtifact, artifactEvent.artifactRef());
            var projected = ledger.getResult(owner, artifactQuery).orElseThrow();
            assertTrue(projected.output().isEmpty());
            assertNull(projected.artifactRef());
            assertEquals(1, preparations.get());
            var artifactMessage = store.claimDue(1, "runtime-terminal-worker", Duration.ofSeconds(30)).getFirst();
            assertEquals(artifactEvent.eventId(), artifactMessage.messageId());
            byte[] artifactBytes = artifactMessage.payload().getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(artifactEvent.canonicalBytes(), artifactBytes);
            var artifactDelivery = new DeliveryContext(artifactMessage.messageId(), artifactMessage.channel(), artifactMessage.destination(),
                    artifactMessage.payload(), artifactMessage.contentType(), artifactMessage.schemaVersion(), artifactMessage.headers(),
                    artifactMessage.traceId(), artifactMessage.attemptCount(), artifactMessage.maxAttempts(), Instant.now().plusSeconds(15));
            when(client.receiveV2(eq(artifactEvent), any(byte[].class), isNull())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertArrayEquals(artifactBytes, call.getArgument(1));
                return new AgentResultReceipt(artifactEvent.eventId(), artifactEvent.fingerprint(), AgentResultReceiptStateEnum.RECEIVED,
                        Instant.now().truncatedTo(ChronoUnit.MICROS));
            });
            permission.set(DecisionEnum.DENY);
            assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(artifactDelivery).kind());
            verify(client, never()).receiveV2(eq(artifactEvent), any(byte[].class), isNull());
            permission.set(DecisionEnum.UNAVAILABLE);
            assertEquals(DeliveryResult.Kind.RETRYABLE_FAILURE, handler.deliver(artifactDelivery).kind());
            verify(client, never()).receiveV2(eq(artifactEvent), any(byte[].class), isNull());
            permission.set(DecisionEnum.ALLOW);
            assertEquals(DeliveryResult.Kind.SUCCESS, handler.deliver(artifactDelivery).kind());
            assertEquals(3, disclosures.get());
            assertTrue(store.markSucceeded(artifactMessage.id(), "runtime-terminal-worker", 0));

            permission.set(DecisionEnum.DENY);
            var deniedCommand = runtimeCommand(ledger, artifactTemplate, "denied-artifact");
            var pendingDenial = linked.submitRuntimeV2(owner, deniedCommand, () -> true);
            var deniedQuery = new GetAgentRunQuery(pendingDenial.runId());
            var deniedClaim = ledger.claimRuntimeV2(owner, deniedQuery, pendingDenial.revision(), "worker-one", Duration.ofSeconds(30));
            var deniedOutput = new AgentExecutionResult(pendingDenial.runId(), AgentRunStatusEnum.COMPLETED, originalArtifact.toWire(),
                    null, null, null, null, "NONE");
            assertEquals(AgentRunStatusEnum.FAILED,
                    ledger.finishRuntimeArtifact(owner, deniedClaim.lease(), deniedClaim.run().revision(), deniedOutput).status());
            var denied = ledger.terminalReferenceV2(owner, deniedQuery, Duration.ofSeconds(5)).orElseThrow();
            assertEquals("TIANJI_PERMISSION_DENIED", denied.errorCode());
            assertNull(denied.output());
            assertNull(denied.artifactRef());
            assertEquals(2, preparations.get());
            assertEquals(4, outboxCount(jdbc));
            }));
        }
    }

    private static Integer outboxCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT count(*) FROM tianji_agent.egon_cola_outbox_message", Integer.class);
    }

    private static ApplicationContextRunner outboxContext(DataSource source, DataSourceTransactionManager manager, DeliveryHandler handler) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TransactionalOutboxAutoConfiguration.class,
                        OutboxJdbcAutoConfiguration.class, OutboxStateMachineAutoConfiguration.class))
                .withBean(DataSource.class, () -> source).withBean(PlatformTransactionManager.class, () -> manager)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean(DeliveryHandler.class, () -> handler).withBean(OutboxAfterCommitBuffer.class, () -> mock(OutboxAfterCommitBuffer.class))
                .withPropertyValues("egon.cola.component.transactional-outbox.enabled=true", "egon.cola.component.transactional-outbox.storage.type=JDBC",
                        "egon.cola.component.transactional-outbox.storage.jdbc.schema=tianji_agent", "egon.cola.component.transactional-outbox.polling.enabled=false",
                        "egon.cola.component.transactional-outbox.cleanup.enabled=false", "egon.cola.component.transactional-outbox.annotation.enabled=false",
                        "egon.cola.component.transactional-outbox.state-machine.execution-timeout=PT1S", "egon.cola.component.transactional-outbox.state-machine.claim-evaluation-timeout=PT3S");
    }

    private static RuntimeFixtureVO provisionRuntime() {
        String url = System.getProperty("tianji.agent.outbox.runtime-v2.postgres.test.url");
        String role = System.getProperty("tianji.agent.outbox.postgres.test.runtime-user");
        String vector = System.getProperty("tianji.runtime.safety.postgres.vector.version");
        if (url == null || !url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || url.equals(System.getProperty("tianji.agent.outbox.postgres.test.url"))
                || role == null || !role.matches("tianji_it_[a-z0-9_]{1,44}")
                || vector == null || !vector.matches("[0-9]+[.][0-9]+[.][0-9]+")) {
            throw new IllegalArgumentException("V2 Outbox test requires its own blank loopback database, runtime role and installed vector patch");
        }
        var owner = new DriverManagerDataSource(url);
        var admin = new JdbcTemplate(owner);
        var source = restrictedRuntime(url, role);
        assertEquals(0, admin.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        assertEquals(vector, admin.queryForObject("""
                SELECT e.extversion FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
                WHERE e.extname='vector' AND n.nspname='public'
                """, String.class));
        var agent = Flyway.configure().dataSource(owner).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                .target("17").cleanDisabled(true).load();
        assertEquals(17, agent.migrate().migrationsExecuted);
        agent.validate();
        var component = Flyway.configure().dataSource(owner).schemas("tianji_agent").defaultSchema("tianji_agent")
                .table("tianji_agent_outbox_schema_history").baselineOnMigrate(true).baselineVersion("0")
                .locations("classpath:db/transactional-outbox/postgresql").cleanDisabled(true).load();
        assertEquals(1, component.migrate().migrationsExecuted);
        component.validate();
        String grantee = grantRuntimeBase(admin, role);
        admin.execute("""
                GRANT SELECT, INSERT, UPDATE ON tianji_agent.agent_runtime_binding,
                    tianji_agent.agent_runtime_call_receipt TO
                """ + grantee);
        admin.execute("GRANT SELECT ON tianji_agent.agent_tool_action_intent, tianji_agent.agent_tool_capability, "
                + "tianji_agent.agent_tool_invocation TO " + grantee);
        return new RuntimeFixtureVO(source, admin);
    }

    private static DriverManagerDataSource restrictedRuntime(String url, String role) {
        if (role == null || !role.matches("tianji_it_[a-z0-9_]{1,44}")) {
            throw new IllegalArgumentException("Outbox test requires its explicit restricted runtime role");
        }
        var source = new DriverManagerDataSource(url);
        source.setUsername(role);
        var jdbc = new JdbcTemplate(source);
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_user=? AND NOT r.rolsuper AND NOT r.rolbypassrls AND NOT r.rolcreaterole
                    AND NOT r.rolcreatedb AND NOT r.rolreplication AND NOT pg_has_role(d.datdba,'MEMBER')
                    AND NOT has_database_privilege(d.oid,'CREATE') AND NOT has_schema_privilege('public','CREATE')
                FROM pg_roles r CROSS JOIN pg_database d WHERE r.rolname=current_user AND d.datname=current_database()
                """, Boolean.class, role)));
        return source;
    }

    private static String grantRuntimeBase(JdbcTemplate admin, String role) {
        String grantee = '"' + role + '"';
        admin.execute("GRANT USAGE ON SCHEMA tianji_agent TO " + grantee);
        admin.execute("""
                GRANT SELECT ON tianji_agent.flyway_schema_history, tianji_agent.tianji_agent_outbox_schema_history,
                    tianji_agent.agent_session, tianji_agent.agent_session_event, tianji_agent.agent_session_scope_state,
                    tianji_agent.agent_session_lease, tianji_agent.agent_run_execution_grant, tianji_agent.agent_budget_reservation TO
                """ + grantee);
        admin.execute("""
                GRANT SELECT, INSERT ON tianji_agent.agent_definition, tianji_agent.agent_definition_version,
                    tianji_agent.agent_event TO
                """ + grantee);
        admin.execute("""
                GRANT SELECT, INSERT, UPDATE ON tianji_agent.agent_run, tianji_agent.agent_process_dispatch,
                    tianji_agent.agent_run_command_receipt, tianji_agent.egon_cola_outbox_message TO
                """ + grantee);
        admin.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA tianji_agent TO " + grantee);
        return grantee;
    }

    private static AgentRuntimeTemplateCommand runtimeTemplate() {
        var empty = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(),
                "required", List.of(), "additionalProperties", false));
        var output = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties",
                Map.of("value", Map.of("type", "string")), "required", List.of("value"), "additionalProperties", false));
        var config = new TemplateConfigurationVO(new TemplateDependencyRefVO("fixture", 1, "a".repeat(64)),
                "model", List.of(), null);
        var limits = new TechnicalLimitsVO(Duration.ofMinutes(2), Duration.ofSeconds(20), Duration.ofSeconds(10), 1, 1, 1, 2000, 100);
        return new AgentRuntimeTemplateCommand("linked-runtime", 1, RuntimeProfileEnum.RUNTIME_V2,
                "agent-executor", empty, output, empty, config, limits);
    }

    private static AgentRuntimeTemplateCommand runtimeArtifactTemplate() {
        var base = runtimeTemplate();
        var schema = ProcessSchemaVO.fromWire(Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "resourceKey", Map.of("type", "string", "maxLength", 128, "pattern", "^[A-Za-z][A-Za-z0-9_.:-]{0,127}$"),
                        "contentSha256", Map.of("type", "string", "maxLength", 64, "pattern", "^[0-9a-f]{64}$"),
                        "mediaType", Map.of("type", "string", "maxLength", 127, "pattern", "^[a-z0-9.+-]{1,63}/[a-z0-9.+-]{1,63}$"),
                        "sizeBytes", Map.of("type", "integer", "minimum", 0, "maximum", ProcessTypedJson.V2_MAX_WIRE_INTEGER)),
                "required", List.of("resourceKey", "contentSha256", "mediaType", "sizeBytes")));
        return new AgentRuntimeTemplateCommand("artifact-runtime", 1, RuntimeProfileEnum.RUNTIME_V2, base.getExecutorRef(),
                base.getInputSchema(), schema, base.getParameterSchema(), base.getConfiguration(), base.getLimits());
    }

    private static LinkedAgentRuntimeCommand runtimeCommand(AgentLedgerService ledger, AgentRuntimeTemplateCommand template,
            String occurrence) {
        var deadline = ledger.databaseNow().plusSeconds(90).truncatedTo(ChronoUnit.MICROS);
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "root", occurrence,
                template.contentSha256(), deadline.minusSeconds(20));
        return new LinkedAgentRuntimeCommand("intent-" + occurrence, link, "node-" + occurrence, template.getTemplateKey(),
                template.getTemplateVersion(), template.contentSha256(), "b".repeat(64), schemaSha(template.getInputSchema()),
                schemaSha(template.getOutputSchema()), Map.of(), Map.of(), template.getLimits(), deadline, "trace-" + occurrence);
    }

    private static String schemaSha(ProcessSchemaVO schema) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ProcessTypedJson.canonicalBytes(schema.toWire())));
        } catch (NoSuchAlgorithmException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private record RuntimeFixtureVO(DataSource source, JdbcTemplate admin) { }

    @Test
    @EnabledIfSystemProperty(named = "tianji.agent.outbox.postgres.test.url", matches = ".+")
    void terminal_fence_and_actual_outbox_commit_atomically_then_deliver_outside_the_snapshot_transaction() {
        String url = System.getProperty("tianji.agent.outbox.postgres.test.url");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) throw new IllegalArgumentException("Outbox test requires an empty loopback tianji_it_ database");
        var databaseOwner = new DriverManagerDataSource(url);
        String role = System.getProperty("tianji.agent.outbox.postgres.test.runtime-user");
        var source = restrictedRuntime(url, role);
        var jdbc = new JdbcTemplate(source);
        var admin = new JdbcTemplate(databaseOwner);
        assertEquals(0, admin.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        var agent = Flyway.configure().dataSource(databaseOwner).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("9").cleanDisabled(true).load();
        agent.migrate(); agent.validate();
        var component = Flyway.configure().dataSource(databaseOwner).schemas("tianji_agent").defaultSchema("tianji_agent")
                .table("tianji_agent_outbox_schema_history").baselineOnMigrate(true).baselineVersion("0")
                .locations("classpath:db/transactional-outbox/postgresql").cleanDisabled(true).load();
        assertEquals(1, component.migrate().migrationsExecuted); component.validate();
        admin.execute("GRANT UPDATE ON tianji_agent.agent_budget_reservation TO " + grantRuntimeBase(admin, role));
        var manager = new DataSourceTransactionManager(source);
        var owner = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "process-worker", "https://idp.example/20");
        var ledgerReference = new AtomicReference<AgentLedgerService>();
        var client = mock(HttpAgentResultClient.class);
        when(client.timeout()).thenReturn(Duration.ofSeconds(3));
        var handler = new AgentResultDeliveryHandler(ledgerReference::get, List.of(new AgentResultDeliveryHandler.Route("process-one", "process-east", owner, client)), Clock.systemUTC());
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TransactionalOutboxAutoConfiguration.class,
                        OutboxJdbcAutoConfiguration.class, OutboxStateMachineAutoConfiguration.class))
                .withBean(DataSource.class, () -> source).withBean(PlatformTransactionManager.class, () -> manager)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean(DeliveryHandler.class, () -> handler).withBean(OutboxAfterCommitBuffer.class, () -> mock(OutboxAfterCommitBuffer.class))
                .withPropertyValues("egon.cola.component.transactional-outbox.enabled=true", "egon.cola.component.transactional-outbox.storage.type=JDBC",
                        "egon.cola.component.transactional-outbox.storage.jdbc.schema=tianji_agent", "egon.cola.component.transactional-outbox.polling.enabled=false",
                        "egon.cola.component.transactional-outbox.cleanup.enabled=false", "egon.cola.component.transactional-outbox.annotation.enabled=false",
                        "egon.cola.component.transactional-outbox.state-machine.execution-timeout=PT1S", "egon.cola.component.transactional-outbox.state-machine.claim-evaluation-timeout=PT3S")
                .run(context -> assertDoesNotThrow(() -> {
                    assertNull(context.getStartupFailure());
                    var publisher = new OutboxAgentTerminalEventPublisher(source, context.getBean(OutboxInfrastructure.class), context.getBean(TransactionalOutbox.class),
                            "agent-east", Map.of("process-east", "process-one"));
                    var ledger = AgentLedgerService.withTerminalEvents(source, publisher); ledgerReference.set(ledger);
                    var linked = new AgentLinkedSubmissionService(source, ledger);
                    var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10), 5, 2, 1000, 500,
                            new BigDecimal("2.00"), "USD", 2, Set.of(), "retry-v1", "approval-v1");
                    var definition = ledger.publish(owner, new AgentDefinitionSpecDTO("review", 1, "SEQUENTIAL", null, "1", "1",
                            new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1"), policy, Map.of("mode", "safe")));
                    var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", definition.contentSha256(),
                            ledger.databaseNow().plusSeconds(30).truncatedTo(ChronoUnit.MICROS));
                    var command = new LinkedAgentStartCommand(new StartAgentRunCommand("review", 1, null, "intent-one", "business", "trace",
                            new AgentInputDTO("private input", List.of(), Map.of()), null), link);
                    var query = new LinkedAgentLookupQuery(command.start().idempotencyKey(), link);
                    var run = linked.submit(owner, command);
                    admin.execute("""
                            CREATE FUNCTION tianji_agent.test_terminal_outbox_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                            BEGIN RAISE EXCEPTION 'controlled Outbox insert failure'; END $$
                            """);
                    admin.execute("CREATE TRIGGER test_terminal_outbox_failure BEFORE INSERT ON tianji_agent.egon_cola_outbox_message "
                            + "FOR EACH ROW EXECUTE FUNCTION tianji_agent.test_terminal_outbox_failure()");
                    assertThrows(RuntimeException.class, () -> linked.close(owner, query));
                    assertEquals(AgentRunStatusEnum.QUEUED, ledger.get(owner, new GetAgentRunQuery(run.runId())).status());
                    assertFalse(linked.resolve(owner, query).orElseThrow().closed());
                    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.egon_cola_outbox_message", Integer.class));
                    admin.execute("DROP TRIGGER test_terminal_outbox_failure ON tianji_agent.egon_cola_outbox_message");
                    assertEquals(AgentRunStatusEnum.CANCELLED, linked.close(owner, query).run().status());
                    var event = ledger.terminalReference(owner, new GetAgentRunQuery(run.runId()), Duration.ofSeconds(5)).orElseThrow();
                    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.egon_cola_outbox_message", Integer.class));
                    var store = context.getBean(OutboxStore.class);
                    var claimed = store.claimDue(1, "terminal-test-worker", Duration.ofSeconds(30)).getFirst();
                    assertEquals(event.eventId(), claimed.messageId());
                    assertFalse(claimed.payload().contains("private input"));
                    assertEquals(Map.of("tianji-event-fingerprint", event.fingerprint()), claimed.headers());
                    when(client.receive(event)).thenAnswer(call -> {
                        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                        return new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, Instant.now().truncatedTo(ChronoUnit.MICROS));
                    });
                    var delivery = new DeliveryContext(claimed.messageId(), claimed.channel(), claimed.destination(), claimed.payload(), claimed.contentType(), claimed.schemaVersion(),
                            claimed.headers(), claimed.traceId(), claimed.attemptCount(), claimed.maxAttempts(), Instant.now().plusSeconds(10));
                    assertEquals(DeliveryResult.Kind.SUCCESS, handler.deliver(delivery).kind());
                    assertTrue(store.markSucceeded(claimed.id(), "terminal-test-worker", 0));
                    verify(client).receive(event);
                }));
    }
}
