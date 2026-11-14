package top.egon.cola.platform.tianji.process.server;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.LinkedMultiValueMap;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessMigrationPreflightDTO;
import top.egon.cola.platform.tianji.process.server.api.ProcessMigrationPreflightController;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Explicit native acceptance only. Select this class alone against a fresh loopback database and temporary runtime role. */
@EnabledIfSystemProperty(named = "tianji.process.postgres.test.url", matches = ".+")
@Isolated("Owns one explicitly supplied fresh PostgreSQL schema")
@Timeout(180)
class ProcessMigrationPreflightPostgresTest {
    private static final String SCHEMA = "tianji_process";
    private static final String DEFINITION_KEY = "migrationTimer";
    private static final String WAIT = "wait";
    private static final String RECOVERY_RECEIPT = "tianji_process.tianji_process_worker_recovery_receipt";

    @Test
    @EnabledIfSystemProperty(named = "tianji.process.postgres.migration-preflight", matches = "true")
    void validates_one_owned_native_timer_without_moving_it() {
        assertEquals(ZoneOffset.UTC, ZoneId.systemDefault().normalized(), "run the fixture with -Duser.timezone=UTC");
        var database = prepareMigrationObservationSchema();

        // Both the fixture JVM and the actual Process JDBC sessions use UTC for native timer timestamps.
        String scopedUtcUrl = database.scopedUrl() + "&options=-c%20TimeZone%3DUTC";
        try (var app = ProcessServerPostgresTest.context(TianjiProcessServerApplication.class, scopedUtcUrl, database.runtimeUser())) {
            var engine = app.getBean(SpringProcessEngineConfiguration.class);
            var dmn = app.getBean(SpringDmnEngineConfiguration.class);
            var process = app.getBean(ProcessEngine.class);
            assertFalse(engine.isAsyncExecutorActivate());

            var validator = new ProcessModelValidator();
            var releases = new ProcessReleaseService(engine, dmn, validator);
            var execution = new ProcessExecutionService(engine, dmn, validator);
            var owner = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "migration-owner", "https://issuer.example/20");
            var foreignIssuer = ProcessActor.verified(owner.tenantId(), owner.subjectType(), owner.subjectId(), "https://foreign.example/20");
            var source = releases.publish(owner, "migration-source", List.of(timerModel("Source timer")));
            var target = releases.publish(owner, "migration-target", List.of(timerModel("Target timer")));
            var sourceDefinition = source.definitions().getFirst();
            var targetDefinition = target.definitions().getFirst();
            assertNotEquals(source.manifestSha256(), target.manifestSha256());
            assertNotEquals(sourceDefinition.engineDefinitionId(), targetDefinition.engineDefinitionId());
            assertTrue(targetDefinition.engineVersion() > sourceDefinition.engineVersion());

            var instance = execution.start(owner, new ProcessExecutionService.StartCommand(source.releaseKey(),
                    DEFINITION_KEY, "migration-order", "migration-start", "migration-correlation", Map.of()));
            assertEquals(ProcessExecutionService.State.ACTIVE, instance.state());
            var initial = snapshot(database.bootstrap(), instance.instanceId());
            assertEquals(2, initial.executions().size());
            assertEquals(1, initial.timers().size());
            assertEquals(1, initial.receipts().size());

            // Keep IAM outside this fixture. The controller and service still check the actual three resource boundaries.
            var access = mock(TianjiProcessAccess.class);
            bindActor(access, owner);
            var preflight = new ProcessMigrationPreflightService(process, dmn, releases, validator, access);
            preflight.verifyReadiness();
            var controller = new ProcessMigrationPreflightController(preflight, access);
            var valid = inspect(controller, instance.instanceId(), target.releaseKey(), target.manifestSha256());
            assertEquals(ProcessMigrationPreflightDTO.Status.NATIVE_STRUCTURE_VALIDATED_ONLY, valid.status());
            assertEquals(instance.instanceId(), valid.instanceId());
            assertEquals(source.releaseKey(), valid.sourceReleaseVersion());
            assertEquals(target.releaseKey(), valid.targetReleaseVersion());
            assertEquals(WAIT, valid.activityId());
            assertNotNull(valid.observedAt());
            var resourceChecks = inOrder(access);
            resourceChecks.verify(access).require(TianjiProcessPermissions.MIGRATION_PREFLIGHT_API);
            resourceChecks.verify(access).require(TianjiProcessPermissions.GET_API);
            resourceChecks.verify(access).require(TianjiProcessPermissions.RELEASE_GET_API);
            resourceChecks.verifyNoMoreInteractions();

            String wrongPin = source.manifestSha256();
            var mismatch = inspect(controller, instance.instanceId(), target.releaseKey(), wrongPin);
            assertEquals(ProcessMigrationPreflightDTO.Status.TARGET_PIN_MISMATCH, mismatch.status());
            assertNull(mismatch.activityId());

            when(access.require(TianjiProcessPermissions.MIGRATION_PREFLIGHT_API)).thenReturn(foreignIssuer);
            assertThrows(AccessDeniedException.class,
                    () -> inspect(controller, instance.instanceId(), target.releaseKey(), target.manifestSha256()));
            bindActor(access, foreignIssuer);
            assertThrows(ProcessResourceNotFoundException.class,
                    () -> inspect(controller, instance.instanceId(), target.releaseKey(), target.manifestSha256()));
            bindActor(access, owner);
            assertEquals(initial, snapshot(database.bootstrap(), instance.instanceId()));

            var runtime = process.getRuntimeService();
            assertEquals(sourceDefinition.engineDefinitionId(), runtime.createProcessInstanceQuery()
                    .processInstanceId(instance.instanceId()).singleResult().getProcessDefinitionId());
            runtime.setVariable(instance.instanceId(), "unexpectedMigrationVariable", "fixture-value");
            var withVariable = snapshot(database.bootstrap(), instance.instanceId());
            assertEquals(1, withVariable.variables().size());
            var unsafe = inspect(controller, instance.instanceId(), target.releaseKey(), target.manifestSha256());
            assertEquals(ProcessMigrationPreflightDTO.Status.ACTIVE_EVIDENCE_UNSAFE, unsafe.status());
            assertNull(unsafe.activityId());
            assertEquals(withVariable, snapshot(database.bootstrap(), instance.instanceId()));
        }
    }

    private static ProcessServerPostgresTest.CaseDatabase prepareMigrationObservationSchema() {
        String url = ProcessServerPostgresTest.explicitTestUrl();
        var source = new DriverManagerDataSource(url);
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA) != 0) {
            throw new IllegalStateException("integration tests require a fresh database without the process schema");
        }
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        var platform = Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_process_schema_history").createSchemas(false).baselineOnMigrate(false).locations("classpath:db/tianji-process")
                .target("10").cleanDisabled(true).load();
        assertEquals(10, platform.migrate().migrationsExecuted);
        platform.validate();
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), jdbc.queryForList(
                "SELECT version FROM " + SCHEMA + ".tianji_process_schema_history ORDER BY installed_rank", String.class));
        var outbox = Flyway.configure().dataSource(source).schemas(SCHEMA).defaultSchema(SCHEMA)
                .baselineOnMigrate(true).baselineVersion("0").locations("classpath:db/transactional-outbox/postgresql")
                .cleanDisabled(true).load();
        outbox.migrate();
        outbox.validate();
        String scoped = url + "?currentSchema=" + SCHEMA;
        try (var installing = ProcessServerPostgresTest.context(ProcessServerPostgresTest.Provisioner.class, scoped,
                System.getProperty("user.name"), "--flowable.database-schema-update=true",
                "--egon.cola.component.transactional-outbox.enabled=false")) {
            assertNotNull(installing.getBean(ProcessEngine.class));
        }

        String configuredRole = System.getProperty("tianji.process.postgres.test.runtime-user", "");
        if (!configuredRole.matches("tianji_it_runtime_[0-9a-f]{12}")) {
            throw new IllegalArgumentException("integration tests require a dedicated temporary runtime role");
        }
        jdbc.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO \"" + configuredRole + "\"");
        String role = ProcessServerPostgresTest.explicitRuntimeRole(jdbc);
        String quoted = "\"" + role + "\"";
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA " + SCHEMA + " TO " + quoted);
        jdbc.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA " + SCHEMA + " TO " + quoted);
        jdbc.execute("REVOKE INSERT, UPDATE, DELETE ON " + SCHEMA + ".flyway_schema_history, " + SCHEMA
                + ".tianji_process_schema_history, " + RECOVERY_RECEIPT + " FROM " + quoted);
        jdbc.execute("REVOKE UPDATE, DELETE ON " + SCHEMA + ".tianji_case_start_provenance, " + SCHEMA
                + ".tianji_case_plan_item_receipt FROM " + quoted);
        jdbc.execute("REVOKE DELETE ON " + SCHEMA + ".tianji_case_completion_inbox FROM " + quoted);
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT has_table_privilege(?, ?, 'SELECT')", Boolean.class,
                role, RECOVERY_RECEIPT));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT has_table_privilege(?, ?, 'INSERT, UPDATE, DELETE, TRUNCATE')",
                Boolean.class, role, RECOVERY_RECEIPT));
        return new ProcessServerPostgresTest.CaseDatabase(scoped, role, jdbc);
    }

    private static ProcessReleaseService.ModelResource timerModel(String name) {
        byte[] xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:migration-postgres-test">
                  <process id="migrationTimer" name="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toWait" sourceRef="start" targetRef="wait"/>
                    <intermediateCatchEvent id="wait">
                      <timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition>
                    </intermediateCatchEvent>
                    <sequenceFlow id="toEnd" sourceRef="wait" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(name).getBytes(StandardCharsets.UTF_8);
        return new ProcessReleaseService.ModelResource(Type.BPMN, DEFINITION_KEY, xml, Map.of());
    }

    private static void bindActor(TianjiProcessAccess access, ProcessActor actor) {
        when(access.require(TianjiProcessPermissions.MIGRATION_PREFLIGHT_API)).thenReturn(actor);
        when(access.require(TianjiProcessPermissions.GET_API)).thenReturn(actor);
        when(access.require(TianjiProcessPermissions.RELEASE_GET_API)).thenReturn(actor);
    }

    private static ProcessMigrationPreflightDTO inspect(ProcessMigrationPreflightController controller, String instanceId,
                                                       String targetRelease, String manifestSha) {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("targetReleaseVersion", targetRelease);
        parameters.add("expectedTargetManifestSha256", manifestSha);
        var response = controller.get(instanceId, parameters);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private static NativeRowsVO snapshot(JdbcTemplate observer, String instanceId) {
        var executions = observer.queryForList("""
                SELECT to_jsonb(e)::text FROM tianji_process.act_ru_execution e
                WHERE e.proc_inst_id_=? OR e.root_proc_inst_id_=? ORDER BY e.id_
                """, String.class, instanceId, instanceId);
        var timers = observer.queryForList("""
                SELECT to_jsonb(t)::text FROM tianji_process.act_ru_timer_job t
                WHERE t.process_instance_id_=? ORDER BY t.id_
                """, String.class, instanceId);
        var receipts = observer.queryForList("""
                SELECT to_jsonb(r)::text FROM tianji_process.tianji_process_command_receipt r
                WHERE r.command_type='START' AND r.resource_id=? ORDER BY r.command_id
                """, String.class, instanceId);
        var variables = observer.queryForList("""
                SELECT to_jsonb(v)::text FROM tianji_process.act_ru_variable v
                WHERE v.proc_inst_id_=? ORDER BY v.id_
                """, String.class, instanceId);
        return new NativeRowsVO(executions, timers, receipts, variables);
    }

    private record NativeRowsVO(List<String> executions, List<String> timers, List<String> receipts, List<String> variables) {
        private NativeRowsVO {
            executions = List.copyOf(executions);
            timers = List.copyOf(timers);
            receipts = List.copyOf(receipts);
            variables = List.copyOf(variables);
        }
    }
}
