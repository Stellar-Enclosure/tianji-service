package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.common.engine.impl.interceptor.CommandConfig;
import org.flowable.common.engine.impl.interceptor.CommandContextInterceptor;
import org.flowable.common.engine.impl.interceptor.LogInterceptor;
import org.flowable.common.engine.impl.interceptor.TransactionContextInterceptor;
import org.flowable.common.spring.SpringTransactionInterceptor;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.ProcessMigrationServiceImpl;
import org.flowable.engine.impl.interceptor.BpmnOverrideContextInterceptor;
import org.flowable.engine.impl.interceptor.CommandInvoker;
import org.flowable.engine.impl.migration.ProcessInstanceMigrationManagerImpl;
import org.flowable.engine.migration.ActivityMigrationMapping;
import org.flowable.engine.migration.ProcessInstanceMigrationBuilder;
import org.flowable.engine.migration.ProcessInstanceMigrationValidationResult;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService.ProcessMigrationPreflightStatusEnum.*;

class ProcessMigrationPreflightServiceTest {
    private static final ProcessActor OWNER = new ProcessActor(20, ProcessActor.SubjectType.USER, "owner", "a".repeat(64));
    private static final Instant OBSERVED = Instant.parse("2026-11-05T10:00:00.123456Z");
    private static final String INSTANCE = "process-1";
    private static final String DEFINITION = "migrationTimer";

    @Test
    void migration_preflight_entry_exists() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService"));
    }

    @Test
    void validates_exact_owned_plain_timer_once() throws Exception {
        try (var fixture = new Fixture()) {
            var actual = assertDoesNotThrow(() -> fixture.service.inspect(fixture.query()));
            assertEquals(new ProcessMigrationPreflightService.ProcessMigrationPreflightDTO(
                    INSTANCE, "release-1", "release-2", NATIVE_STRUCTURE_VALIDATED_ONLY, "wait", OBSERVED), actual);

            var guard = inOrder(fixture.access, fixture.source);
            guard.verify(fixture.access).require(TianjiProcessPermissions.GET_API);
            guard.verify(fixture.access).require(TianjiProcessPermissions.RELEASE_GET_API);
            guard.verify(fixture.source).getConnection();
            verify(fixture.connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            verify(fixture.connection).commit();
            verify(fixture.connection).close();

            var mapping = ArgumentCaptor.forClass(ActivityMigrationMapping.class);
            var nativeCall = inOrder(fixture.migration, fixture.builder, fixture.validation);
            nativeCall.verify(fixture.migration).createProcessInstanceMigrationBuilder();
            nativeCall.verify(fixture.builder).migrateToProcessDefinition("native-2");
            nativeCall.verify(fixture.builder).addActivityMigrationMapping(mapping.capture());
            nativeCall.verify(fixture.builder).validateMigration(INSTANCE);
            nativeCall.verify(fixture.validation).isMigrationValid();
            nativeCall.verifyNoMoreInteractions();
            assertEquals(List.of("wait"), mapping.getValue().getFromActivityIds());
            assertEquals(List.of("wait"), mapping.getValue().getToActivityIds());
            assertEquals(List.of("SET TRANSACTION READ ONLY", "SET LOCAL statement_timeout = '2s'"),
                    fixture.sql.subList(0, 2));
            assertEquals(1, fixture.sql.stream().filter(sql -> sql.contains("clock_timestamp()")).count());
            assertTrue(fixture.sql.getLast().contains("transaction_read_only"));
            assertFalse(TransactionSynchronizationManager.hasResource(fixture.source));
        }
    }

    @Test
    void denies_foreign_or_unsafe_evidence() throws Exception {
        try (var fixture = new Fixture()) {
            when(fixture.access.require(TianjiProcessPermissions.RELEASE_GET_API))
                    .thenReturn(new ProcessActor(20, ProcessActor.SubjectType.USER, "foreign", "b".repeat(64)));
            assertThrows(AccessDeniedException.class, () -> fixture.service.inspect(fixture.query()));
            verifyNoInteractions(fixture.source, fixture.builder);
        }
        try (var fixture = new Fixture()) {
            fixture.startPresent = false;
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.inspect(fixture.query()));
            assertTrue(fixture.sql.stream().noneMatch(sql -> sql.contains("FROM \"tianji_process\".tianji_process_release")));
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            fixture.targetPresent = false;
            assertEquals(TARGET_UNAVAILABLE, fixture.service.inspect(fixture.query()).status());
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            var wrong = new ProcessMigrationPreflightService.ValidateProcessMigrationQuery(INSTANCE, "release-2", "b".repeat(64));
            assertEquals(TARGET_PIN_MISMATCH, fixture.service.inspect(wrong).status());
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            fixture.extraExecution = true;
            assertEquals(ACTIVE_EVIDENCE_UNSAFE, fixture.service.inspect(fixture.query()).status());
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            fixture.exactTimer = false;
            assertEquals(ACTIVE_EVIDENCE_UNSAFE, fixture.service.inspect(fixture.query()).status());
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            fixture.linkedTable = "tianji_case_start_provenance";
            assertEquals(ACTIVE_EVIDENCE_UNSAFE, fixture.service.inspect(fixture.query()).status());
            verifyNoInteractions(fixture.builder);
        }
    }

    @Test
    void binds_physical_read_only_and_catalog() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.catalogReadable = false;
            var error = assertThrows(IllegalStateException.class, () -> fixture.service.inspect(fixture.query()));
            assertEquals("Process migration preflight source is unavailable", error.getMessage());
            assertNull(error.getCause());
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            fixture.readOnly = false;
            assertThrows(IllegalStateException.class, () -> fixture.service.inspect(fixture.query()));
            assertTrue(fixture.sql.stream().noneMatch(sql -> sql.contains("clock_timestamp()")));
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            fixture.clockPresent = false;
            assertThrows(IllegalStateException.class, () -> fixture.service.inspect(fixture.query()));
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            when(fixture.engine.getCommandInterceptors()).thenReturn(List.of(mock(SpringTransactionInterceptor.class)));
            assertThrows(IllegalStateException.class, () -> fixture.service.inspect(fixture.query()));
            verifyNoInteractions(fixture.builder);
        }
        try (var fixture = new Fixture()) {
            when(fixture.validation.isMigrationValid()).thenReturn(false);
            assertEquals(NATIVE_REJECTED, fixture.service.inspect(fixture.query()).status());
            verify(fixture.validation, never()).getValidationMessages();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final TimeZone previous = TimeZone.getDefault();
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final ProcessEngine process = mock(ProcessEngine.class);
        private final SpringProcessEngineConfiguration engine = mock(SpringProcessEngineConfiguration.class);
        private final SpringDmnEngineConfiguration dmn = mock(SpringDmnEngineConfiguration.class);
        private final RepositoryService repository = mock(RepositoryService.class);
        private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
        private final ProcessMigrationServiceImpl migration = mock(ProcessMigrationServiceImpl.class);
        private final ProcessInstanceMigrationBuilder builder = mock(ProcessInstanceMigrationBuilder.class, RETURNS_SELF);
        private final ProcessInstanceMigrationValidationResult validation = mock(ProcessInstanceMigrationValidationResult.class);
        private final List<String> sql = new ArrayList<>();
        private final byte[] sourceXml = xml("Source");
        private final byte[] targetXml = xml("Target");
        private final String sourceManifest = manifest(sourceXml);
        private final String targetManifest = manifest(targetXml);
        private final MockedConstruction<JdbcTemplate> jdbc;
        private final ProcessMigrationPreflightService service;
        private boolean startPresent = true;
        private boolean targetPresent = true;
        private boolean extraExecution;
        private boolean exactTimer = true;
        private boolean catalogReadable = true;
        private boolean readOnly = true;
        private boolean clockPresent = true;
        private String linkedTable;

        private Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            var manager = new DataSourceTransactionManager(source);
            when(process.getProcessEngineConfiguration()).thenReturn(engine);
            when(engine.getDataSource()).thenReturn(source);
            when(engine.getTransactionManager()).thenReturn(manager);
            when(engine.getDatabaseSchema()).thenReturn("tianji_process");
            when(engine.getRepositoryService()).thenReturn(repository);
            when(engine.getRuntimeService()).thenReturn(mock(RuntimeService.class));
            when(engine.getHistoryService()).thenReturn(mock(HistoryService.class));
            when(engine.getTaskService()).thenReturn(mock(TaskService.class));
            when(engine.getManagementService()).thenReturn(mock(ManagementService.class));
            when(engine.isDbHistoryUsed()).thenReturn(true);
            when(engine.getHistoryLevel()).thenReturn(HistoryLevel.INSTANCE);
            when(engine.getBeans()).thenReturn(Map.of());
            when(engine.getDefaultCommandConfig()).thenReturn(new CommandConfig());
            when(engine.getProcessInstanceMigrationManager()).thenReturn(mock(ProcessInstanceMigrationManagerImpl.class));
            when(engine.getCommandInterceptors()).thenReturn(List.of(mock(LogInterceptor.class),
                    mock(SpringTransactionInterceptor.class), mock(CommandContextInterceptor.class),
                    mock(TransactionContextInterceptor.class), mock(BpmnOverrideContextInterceptor.class), mock(CommandInvoker.class)));
            when(engine.getProcessMigrationService()).thenReturn(migration);
            when(process.getProcessMigrationService()).thenReturn(migration);
            when(migration.createProcessInstanceMigrationBuilder()).thenReturn(builder);
            when(builder.validateMigration(INSTANCE)).thenReturn(validation);
            when(validation.isMigrationValid()).thenReturn(true);
            when(dmn.getDataSource()).thenReturn(source);
            when(dmn.getTransactionManager()).thenReturn(manager);
            when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
            when(dmn.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
            when(dmn.getBeans()).thenReturn(Map.of());
            when(access.require(TianjiProcessPermissions.GET_API)).thenReturn(OWNER);
            when(access.require(TianjiProcessPermissions.RELEASE_GET_API)).thenReturn(OWNER);
            var sourceDefinition = definition(1);
            var targetDefinition = definition(2);
            when(repository.createProcessDefinitionQuery()).thenAnswer(ignored -> {
                var query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
                when(query.processDefinitionId(anyString())).thenAnswer(call -> {
                    String id = call.getArgument(0);
                    when(query.singleResult()).thenReturn("native-1".equals(id) ? sourceDefinition : targetDefinition);
                    return query;
                });
                return query;
            });
            when(repository.getResourceAsStream("deployment-1", DEFINITION + ".bpmn20.xml"))
                    .thenAnswer(ignored -> new ByteArrayInputStream(sourceXml));
            when(repository.getResourceAsStream("deployment-2", DEFINITION + ".bpmn20.xml"))
                    .thenAnswer(ignored -> new ByteArrayInputStream(targetXml));

            jdbc = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::jdbcCall),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            try {
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                var validator = new ProcessModelValidator();
                var releases = new ProcessReleaseService(engine, dmn, validator);
                service = new ProcessMigrationPreflightService(process, dmn, releases, validator, access);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private ProcessMigrationPreflightService.ValidateProcessMigrationQuery query() {
            return new ProcessMigrationPreflightService.ValidateProcessMigrationQuery(INSTANCE, "release-2", targetManifest);
        }

        private Object jdbcCall(InvocationOnMock call) throws Exception {
            if ("setQueryTimeout".equals(call.getMethod().getName())) return null;
            if ("getDataSource".equals(call.getMethod().getName())) return source;
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            var arguments = call.getRawArguments();
            String statement = (String) arguments[0];
            sql.add(statement);
            return switch (call.getMethod().getName()) {
                case "execute" -> {
                    assertTrue(List.of("SET TRANSACTION READ ONLY", "SET LOCAL statement_timeout = '2s'").contains(statement));
                    yield null;
                }
                case "queryForObject" -> scalar(statement);
                case "query" -> queryRows(statement, (RowMapper<?>) arguments[1], (Object[]) arguments[2]);
                default -> throw new AssertionError("Unexpected JDBC operation " + call.getMethod().getName());
            };
        }

        private Object scalar(String statement) {
            if (statement.contains("clock_timestamp()")) return clockPresent ? Timestamp.from(OBSERVED) : null;
            if (statement.contains("transaction_read_only")) {
                assertTrue(statement.contains("transaction_isolation"));
                assertTrue(statement.contains("TimeZone"));
                return readOnly;
            }
            if (statement.contains("pg_class")) {
                assertTrue(statement.contains("NOT pg_catalog.has_table_privilege"));
                assertTrue(statement.contains("relrowsecurity"));
                return catalogReadable;
            }
            if (statement.contains("pg_is_in_recovery()")) return true;
            if (statement.contains("SELECT EXISTS")) return linkedTable != null && statement.contains(linkedTable);
            throw new AssertionError("Unexpected scalar statement");
        }

        private List<?> queryRows(String statement, RowMapper<?> mapper, Object[] arguments) throws Exception {
            if (statement.contains("tianji_process_command_receipt")) {
                assertArrayEquals(new Object[]{OWNER.tenantId(), "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), INSTANCE}, arguments);
                if (!startPresent) return List.of();
                return map(mapper, List.of(Map.of("subject_type", "USER", "subject_issuer", OWNER.issuerFingerprint(),
                        "subject_id", OWNER.subjectId(), "command_id", "start-1", "request_sha256", "c".repeat(64),
                        "release_key", "release-1", "correlation_id", "corr-1", "resource_id", INSTANCE,
                        "created_at", Timestamp.from(OBSERVED.minusSeconds(1)))));
            }
            if (statement.contains("tianji_process_release_definition")) {
                boolean target = "release-2".equals(arguments[1]);
                return map(mapper, List.of(Map.of("model_type", "BPMN", "definition_key", DEFINITION,
                        "engine_definition_id", target ? "native-2" : "native-1",
                        "engine_deployment_id", target ? "deployment-2" : "deployment-1", "engine_version", target ? 2 : 1,
                        "model_sha256", sha(target ? targetXml : sourceXml), "input_schema", "{}")));
            }
            if (statement.contains("tianji_process_release")) {
                assertEquals(OWNER.tenantId(), arguments[0]);
                boolean target = "release-2".equals(arguments[1]);
                if (target && !targetPresent) return List.of();
                return map(mapper, List.of(Map.of("manifest_sha256", target ? targetManifest : sourceManifest,
                        "state", "ACTIVE", "created_at", Timestamp.from(OBSERVED.minusSeconds(10)))));
            }
            if (statement.contains("act_ru_execution") && statement.contains("parent_id_ IS NULL")) {
                assertArrayEquals(new Object[]{INSTANCE, INSTANCE, "20"}, arguments);
                return map(mapper, List.of(Map.of("id_", INSTANCE, "rev_", 1, "proc_def_id_", "native-1",
                        "business_key_", "business-1", "suspension_state_", 1, "tenant_id_", "20", "proc_inst_id_", INSTANCE)));
            }
            if (statement.contains("act_ru_execution")) {
                assertTrue(statement.contains("root_proc_inst_id_=? LIMIT 3"));
                assertArrayEquals(new Object[]{INSTANCE, INSTANCE}, arguments);
                var rows = new ArrayList<>(List.of(execution(true, INSTANCE), execution(false, "child")));
                if (extraExecution) rows.add(execution(false, "child-extra"));
                return map(mapper, rows);
            }
            if (statement.contains("act_ru_timer_job")) {
                assertTrue(statement.contains("AT TIME ZONE 'UTC' > ?"));
                assertTrue(statement.contains("LIMIT 2"));
                assertArrayEquals(new Object[]{"20", "native-1", INSTANCE, "child", "wait", "trigger-timer",
                        "{\"activityId\":\"wait\"}", OffsetDateTime.parse(OBSERVED.toString()), INSTANCE, INSTANCE, "child"}, arguments);
                return map(mapper, List.of(Map.of("exact_timer", exactTimer)));
            }
            throw new AssertionError("Unexpected scoped query");
        }

        private static Map<String, Object> execution(boolean root, String id) {
            var row = new HashMap<String, Object>();
            row.put("id_", id);
            row.put("rev_", 1);
            row.put("proc_inst_id_", INSTANCE);
            row.put("root_proc_inst_id_", INSTANCE);
            row.put("parent_id_", root ? null : INSTANCE);
            row.put("proc_def_id_", "native-1");
            row.put("tenant_id_", "20");
            row.put("act_id_", root ? null : "wait");
            row.put("suspension_state_", 1);
            row.put("is_active_", true);
            row.put("is_scope_", root);
            row.put("is_concurrent_", false);
            row.put("is_event_scope_", false);
            row.put("is_mi_root_", false);
            row.put("unlinked", true);
            row.put("unlocked", true);
            return row;
        }

        private static List<?> map(RowMapper<?> mapper, List<? extends Map<String, Object>> rows) throws SQLException {
            var result = new ArrayList<>();
            for (var row : rows) {
                var rs = mock(ResultSet.class, call -> {
                    String field = call.getArgument(0);
                    if (!row.containsKey(field)) throw new AssertionError("Unexpected selected field " + field);
                    return row.get(field);
                });
                result.add(mapper.mapRow(rs, result.size()));
            }
            return result;
        }

        @Override
        public void close() {
            try {
                jdbc.close();
            } finally {
                TimeZone.setDefault(previous);
            }
        }
    }

    private static ProcessDefinition definition(int version) {
        var definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn("native-" + version);
        when(definition.getDeploymentId()).thenReturn("deployment-" + version);
        when(definition.getKey()).thenReturn(DEFINITION);
        when(definition.getVersion()).thenReturn(version);
        when(definition.getResourceName()).thenReturn(DEFINITION + ".bpmn20.xml");
        return definition;
    }

    private static byte[] xml(String name) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:migration-test">
                  <process id="migrationTimer" name="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toWait" sourceRef="start" targetRef="wait"/>
                    <intermediateCatchEvent id="wait">
                      <timerEventDefinition><timeDuration>PT30S</timeDuration></timerEventDefinition>
                    </intermediateCatchEvent>
                    <sequenceFlow id="toEnd" sourceRef="wait" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(name).getBytes(StandardCharsets.UTF_8);
    }

    private static String manifest(byte[] model) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeInt(1);
            for (byte[] part : List.of("BPMN".getBytes(StandardCharsets.UTF_8),
                    DEFINITION.getBytes(StandardCharsets.UTF_8), model)) {
                output.writeInt(part.length);
                output.write(part);
            }
            output.writeInt(0);
        }
        return sha(bytes.toByteArray());
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
