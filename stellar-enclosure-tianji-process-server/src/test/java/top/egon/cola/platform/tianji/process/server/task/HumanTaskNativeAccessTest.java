package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskSelectionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controlled same-transaction/parameter fences. Native PostgreSQL lock scheduling is not executed by these markers. */
class HumanTaskNativeAccessTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://idp.example/20");

    @Test void ambient_or_foreign_storage_cannot_become_the_owning_native_transaction() {
        var source = mock(DataSource.class); var engine = engine(source); var access = new HumanTaskNativeAccess(source, engine);
        assertThrows(IllegalStateException.class, () -> access.find(context("get", Set.of("50")), "task", false)); verifyNoInteractions(source);
        when(engine.getDatabaseTablePrefix()).thenReturn("other_"); assertThrows(IllegalArgumentException.class, () -> new HumanTaskNativeAccess(source, engine));
        when(engine.getDatabaseTablePrefix()).thenReturn(""); when(engine.getDataSource()).thenReturn(mock(DataSource.class)); assertThrows(IllegalArgumentException.class, () -> new HumanTaskNativeAccess(source, engine));
    }

    @Test void mutation_lock_joins_the_exact_start_namespace_before_the_native_row_is_acquired() throws Exception {
        var source = source(); var engine = engine(source); var jdbc = new AtomicReference<JdbcTemplate>(); var named = new AtomicReference<NamedParameterJdbcTemplate>();
        try (var statements = statements(jdbc); var parameters = parameters(named)) {
            var access = new HumanTaskNativeAccess(source, engine); var context = context("claim", Set.of("50")); var transaction = new TransactionTemplate((DataSourceTransactionManager) engine.getTransactionManager());
            var query = mock(TaskQuery.class, RETURNS_SELF); var task = mock(Task.class); when(task.getId()).thenReturn("task"); when(task.getTenantId()).thenReturn("20");
            when(engine.getTaskService().createTaskQuery()).thenReturn(query); when(query.singleResult()).thenReturn(task);
            assertSame(task, transaction.execute(status -> access.find(context, "task", true).orElseThrow()));
            verify(named.get()).query(argThat(sql -> sql.contains("FOR UPDATE OF t") && sql.contains("s.subject_issuer=:issuer") && sql.contains("s.state='APPLIED'")
                            && sql.contains("s.resource_id=t.proc_inst_id_") && !sql.contains("s.subject_id=")), argThat((MapSqlParameterSource values) -> values.getValue("tenant").equals(20L)
                            && values.getValue("issuer").equals(ACTOR.issuerFingerprint()) && values.getValue("task").equals("task")), org.mockito.ArgumentMatchers.<RowMapper<String>>any());
            verify(query).taskTenantId("20"); assertThrows(IllegalStateException.class, () -> transaction.execute(status -> access.find(context("get", Set.of("50")), "task", true)));
        }
    }

    @Test void candidate_assignee_and_admin_queries_keep_their_scope_filters_and_exclusive_bounded_cursor() throws Exception {
        var source = source(); var engine = engine(source); var jdbc = new AtomicReference<JdbcTemplate>(); var named = new AtomicReference<NamedParameterJdbcTemplate>();
        try (var statements = statements(jdbc); var parameters = parameters(named)) {
            var access = new HumanTaskNativeAccess(source, engine); var transaction = new TransactionTemplate((DataSourceTransactionManager) engine.getTransactionManager());
            var query = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.CANDIDATE, "release", "instance", "review", "before", 5);
            assertEquals(List.of("task"), transaction.execute(status -> access.candidates(context("query", Set.of("50")), query, 50)));
            verify(named.get()).query(argThat(sql -> sql.contains("i.group_id_ IN (:groups)") && sql.contains("t.assignee_ IS NULL") && sql.contains("t.id_ COLLATE \"C\">:after")
                            && sql.contains("LIMIT :maximum") && sql.contains("s.release_key=:release")), argThat((MapSqlParameterSource values) -> values.getValue("groups").equals(Set.of("role:50"))
                            && values.getValue("maximum").equals(50) && values.getValue("scope").equals("instance") && values.getValue("after").equals("before")), org.mockito.ArgumentMatchers.<RowMapper<String>>any());
            clearInvocations(named.get()); transaction.executeWithoutResult(status -> access.candidates(context("adminQuery", Set.of()), query, 5));
            verify(named.get()).query(argThat(sql -> !sql.contains("group_id_") && sql.contains("t.assignee_ IS NULL") && sql.contains("s.subject_issuer=:issuer")), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<String>>any());
            clearInvocations(named.get()); assertEquals(List.of(), transaction.execute(status -> access.candidates(context("query", Set.of()), query, 5))); verifyNoInteractions(named.get());
        }
    }

    @Test void waiting_past_the_context_deadline_never_reaches_the_native_mutation_api() throws Exception {
        var source = source(); var engine = engine(source); var jdbc = new AtomicReference<JdbcTemplate>(); var named = new AtomicReference<NamedParameterJdbcTemplate>();
        try (var statements = statements(jdbc); var parameters = parameters(named)) {
            var access = new HumanTaskNativeAccess(source, engine); var transaction = new TransactionTemplate((DataSourceTransactionManager) engine.getTransactionManager());
            when(jdbc.get().queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(31)));
            assertThrows(IllegalStateException.class, () -> transaction.execute(status -> access.find(context("complete", Set.of("50")), "task", true)));
            verify(engine.getTaskService(), never()).createTaskQuery();
        }
    }

    @Test void case_scans_and_unlocked_write_discovery_bind_owner_realm_and_cmmn_scope() throws Exception {
        var source=source(); var engine=engine(source); var cmmn=mock(org.flowable.cmmn.spring.SpringCmmnEngineConfiguration.class);
        var tasks=mock(org.flowable.cmmn.api.CmmnTaskService.class); var manager=engine.getTransactionManager(); when(cmmn.getDataSource()).thenReturn(source); when(cmmn.getTransactionManager()).thenReturn(manager);
        when(cmmn.getDatabaseSchema()).thenReturn("tianji_process"); when(cmmn.getCmmnTaskService()).thenReturn(tasks);
        var jdbc=new AtomicReference<JdbcTemplate>(); var named=new AtomicReference<NamedParameterJdbcTemplate>();
        try(var statements=statements(jdbc);var parameters=parameters(named)) {
            var access=new HumanTaskNativeAccess(source,engine,cmmn); var tx=new TransactionTemplate((DataSourceTransactionManager)engine.getTransactionManager());
            var query=new ListHumanTasksQuery(HumanTaskScopeTypeEnum.CASE,HumanTaskSelectionEnum.CANDIDATE,"release","case-1","primaryTask","before",5);
            assertEquals(List.of("task"),assertDoesNotThrow(()->tx.execute(s->access.candidates(context("query",Set.of("7")),query,5))));
            verify(named.get()).query(argThat(sql->sql.contains("scope_type_='cmmn'") && sql.contains("p.owner_issuer=:issuer") && sql.contains("t.scope_id_=:scope") && sql.contains("COLLATE \"C\"") && !sql.contains("FOR UPDATE")),any(MapSqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<String>>any());
            var nativeQuery=mock(TaskQuery.class,RETURNS_SELF); var task=mock(Task.class);
            when(task.getId()).thenReturn("task");when(task.getTenantId()).thenReturn("20");when(task.getScopeType()).thenReturn("cmmn");when(task.getScopeId()).thenReturn("case-1");when(task.getScopeDefinitionId()).thenReturn("case-definition");when(task.getSubScopeId()).thenReturn("plan-1");when(task.getTaskDefinitionKey()).thenReturn("primaryTask");
            when(tasks.createTaskQuery()).thenReturn(nativeQuery);when(nativeQuery.singleResult()).thenReturn(task);
            assertEquals(Optional.of(task),tx.execute(s->access.findCase(context("claim",Set.of("7")),"task",false)));
            verify(named.get(),never()).query(contains("FOR UPDATE"),any(MapSqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<String>>any());
            assertEquals(Optional.of(task),tx.execute(s->access.findCase(context("claim",Set.of("7")),"task",true)));
            verify(named.get()).query(contains("FOR UPDATE OF t"),any(MapSqlParameterSource.class),org.mockito.ArgumentMatchers.<RowMapper<String>>any());
            verifyNoInteractions(engine.getTaskService());
        }
    }

    @Test void case_reference_queries_bind_the_exact_active_human_plan_item_before_the_task_fence() throws Exception {
        var source = source(); var engine = engine(source); var manager = engine.getTransactionManager();
        var cmmn = mock(org.flowable.cmmn.spring.SpringCmmnEngineConfiguration.class); var tasks = mock(org.flowable.cmmn.api.CmmnTaskService.class);
        when(cmmn.getDataSource()).thenReturn(source); when(cmmn.getTransactionManager()).thenReturn(manager); when(cmmn.getDatabaseSchema()).thenReturn("tianji_process"); when(cmmn.getCmmnTaskService()).thenReturn(tasks);
        var jdbc = new AtomicReference<JdbcTemplate>(); var named = new AtomicReference<NamedParameterJdbcTemplate>();
        try (var statements = statements(jdbc); var parameters = parameters(named)) {
            var access = new HumanTaskNativeAccess(source, engine, cmmn); var tx = new TransactionTemplate((DataSourceTransactionManager) manager);
            var sqls = new java.util.ArrayList<String>(); var bindings = new java.util.ArrayList<MapSqlParameterSource>();
            when(named.get().query(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<String>>any())).thenAnswer(call -> {
                sqls.add(call.getArgument(0)); bindings.add(call.getArgument(1)); return List.of("task");
            });
            var query = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.CASE, HumanTaskSelectionEnum.CANDIDATE, null, null, null, null, 5);
            tx.executeWithoutResult(status -> access.candidates(context("query", Set.of("7")), query, 5));
            var taskQuery = mock(TaskQuery.class, RETURNS_SELF); var task = mock(Task.class);
            when(task.getId()).thenReturn("task"); when(task.getTenantId()).thenReturn("20"); when(task.getScopeType()).thenReturn("cmmn"); when(task.getScopeId()).thenReturn("case-1");
            when(task.getScopeDefinitionId()).thenReturn("case-definition"); when(task.getSubScopeId()).thenReturn("plan-1"); when(task.getTaskDefinitionKey()).thenReturn("primaryTask");
            when(tasks.createTaskQuery()).thenReturn(taskQuery); when(taskQuery.singleResult()).thenReturn(task);
            tx.executeWithoutResult(status -> access.findCase(context("claim", Set.of("7")), "task", true));
            assertEquals(2, sqls.size());
            for (int index = 0; index < sqls.size(); index++) {
                String sql = sqls.get(index); var values = bindings.get(index).getValues();
                assertAll("CASE affinity SQL contract, not a PostgreSQL execution claim",
                        () -> assertTrue(sql.contains("JOIN \"tianji_process\".act_cmmn_ru_plan_item_inst i")),
                        () -> assertTrue(sql.contains("i.id_=t.sub_scope_id_")),
                        () -> assertTrue(sql.contains("i.case_inst_id_=t.scope_id_")),
                        () -> assertTrue(sql.contains("i.case_def_id_=t.scope_definition_id_")),
                        () -> assertTrue(sql.contains("i.tenant_id_=t.tenant_id_")),
                        () -> assertTrue(sql.contains("i.item_definition_id_=t.task_def_key_")),
                        () -> assertTrue(sql.contains("i.item_definition_type_=:humanTaskKind")),
                        () -> assertTrue(sql.contains("i.state_=:activeState")),
                        () -> assertTrue(sql.contains("i.ended_time_ IS NULL")),
                        () -> assertEquals("humantask", values.get("humanTaskKind")),
                        () -> assertEquals(org.flowable.cmmn.api.runtime.PlanItemDefinitionType.HUMAN_TASK, values.get("humanTaskKind")),
                        () -> assertEquals(org.flowable.cmmn.api.runtime.PlanItemInstanceState.ACTIVE, values.get("activeState")));
            }
        }
    }

    private static org.mockito.MockedConstruction<JdbcTemplate> statements(AtomicReference<JdbcTemplate> captured) {
        return mockConstruction(JdbcTemplate.class, (value, ignored) -> { captured.set(value); when(value.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(true);
            when(value.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW)); });
    }
    private static org.mockito.MockedConstruction<NamedParameterJdbcTemplate> parameters(AtomicReference<NamedParameterJdbcTemplate> captured) {
        return mockConstruction(NamedParameterJdbcTemplate.class, (value, ignored) -> { captured.set(value); when(value.query(anyString(), any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<String>>any())).thenReturn(List.of("task")); });
    }
    private static HumanTaskContext context(String resource, Set<String> roles) { return new HumanTaskContext(ACTOR, "tianji.api.process.task." + resource, roles, NOW.plusSeconds(30), 2, 3); }
    private static SpringProcessEngineConfiguration engine(DataSource source) { var engine = mock(SpringProcessEngineConfiguration.class); when(engine.getDataSource()).thenReturn(source); when(engine.getTransactionManager()).thenReturn(new DataSourceTransactionManager(source)); when(engine.getDatabaseSchema()).thenReturn("tianji_process"); when(engine.getTaskService()).thenReturn(mock(TaskService.class)); return engine; }
    private static DataSource source() throws Exception { var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true); return source; }
}
