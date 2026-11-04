package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.cmmn.api.CmmnHistoryService;
import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.api.runtime.CaseInstanceQuery;
import org.flowable.cmmn.api.runtime.PlanItemInstanceQuery;
import org.flowable.cmmn.api.history.HistoricCaseInstance;
import org.flowable.cmmn.api.history.HistoricCaseInstanceQuery;
import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.history.HistoricPlanItemInstanceQuery;
import org.flowable.cmmn.engine.impl.persistence.entity.PlanItemInstanceEntity;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CaseNativeAccessTest {
    @Test void native_access_rejects_a_foreign_engine_storage_identity() {
        var source=mock(DataSource.class);var config=mock(SpringCmmnEngineConfiguration.class);
        when(config.getDataSource()).thenReturn(mock(DataSource.class));when(config.getDatabaseSchema()).thenReturn("tianji_process");
        assertThrows(IllegalArgumentException.class,()->new CaseNativeAccess(source,config));
    }
    @Test void actual_owning_transaction_and_current_deadline_are_required_before_native_io() throws Exception {
        try(var f=new Fixture()){
            assertThrows(IllegalStateException.class,()->f.access.current(context()));verifyNoInteractions(f.runtime,f.history);
            when(f.jdbc.get().queryForObject(eq("SELECT clock_timestamp()"),eq(Timestamp.class))).thenReturn(Timestamp.from(CaseSnapshotTest.NOW.plusSeconds(31)));
            assertThrows(IllegalStateException.class,()->f.tx.execute(s->f.access.current(context())));
        }
    }
    @Test void native_plan_fence_binds_exact_tenant_case_definition_and_item_and_never_updates_engine_tables() throws Exception {
        try(var f=new Fixture()){
            f.tx.executeWithoutResult(s->f.access.lockPlanItem(CaseSnapshotTest.origin(),"pi-primary"));
            verify(f.jdbc.get()).query(argThat(sql->sql.contains("act_cmmn_ru_plan_item_inst")&&sql.contains("FOR UPDATE OF p")&&sql.contains("p.case_inst_id_=?")&&sql.contains("p.case_def_id_=?")&&sql.contains("p.tenant_id_=?")&&sql.contains("p.id_=?")),any(RowMapper.class),eq("case-1"),eq("case-def"),eq("20"),eq("pi-primary"));
            verify(f.jdbc.get(),never()).update(anyString(),any(Object[].class));
        }
    }
    @Test void absent_runtime_and_historical_case_is_not_fabricated_as_completed() throws Exception {
        try(var f=new Fixture()){
            assertThrows(IllegalStateException.class,()->f.tx.execute(s->f.access.observe(CaseSnapshotTest.origin())));
        }
    }
    @Test void runtime_observation_includes_ended_items_and_rechecks_native_source_affinity() throws Exception {
        try(var f=new Fixture()){
            var item=f.runtimeItem();
            var result=assertDoesNotThrow(()->f.tx.execute(s->f.access.observe(CaseSnapshotTest.origin())));
            assertNotNull(result);assertEquals("active",result.state());assertNull(result.finishedAt());
            assertEquals(1,result.items().size());assertEquals("pi-primary",result.items().getFirst().id());assertEquals(1,result.items().getFirst().revision());assertFalse(result.items().getFirst().historical());
            verify(f.planQuery).includeEnded();verify(f.planQuery).caseInstanceId("case-1");verify(f.planQuery).caseDefinitionId("case-def");verify(f.planQuery).planItemInstanceTenantId("20");
            when(item.getCaseInstanceId()).thenReturn("foreign-case");
            assertThrows(IllegalStateException.class,()->f.tx.execute(s->f.access.observe(CaseSnapshotTest.origin())));
        }
    }
    @Test void active_native_human_task_observation_copies_its_exact_scope_assignee_and_revision() throws Exception {
        try(var f=new Fixture()){
            var item=f.runtimeItem();when(item.getState()).thenReturn("active");
            var query=mock(org.flowable.task.api.TaskQuery.class,RETURNS_SELF);when(f.tasks.createTaskQuery()).thenReturn(query);
            var task=mock(org.flowable.task.service.impl.persistence.entity.TaskEntity.class);
            when(task.getTenantId()).thenReturn("20");when(task.getScopeType()).thenReturn("cmmn");when(task.getScopeId()).thenReturn("case-1");when(task.getScopeDefinitionId()).thenReturn("case-def");when(task.getSubScopeId()).thenReturn("pi-primary");when(task.getTaskDefinitionKey()).thenReturn("primaryTask");when(task.getAssignee()).thenReturn("native-assignee");when(task.getRevision()).thenReturn(4);
            when(query.listPage(0,2)).thenReturn(List.of(task));
            var result=assertDoesNotThrow(()->f.tx.execute(status->f.access.observe(CaseSnapshotTest.origin())));assertNotNull(result);
            assertEquals("native-assignee",result.items().getFirst().assignee());assertEquals(4,result.items().getFirst().taskRevision());
            verify(query).taskTenantId("20");verify(query).scopeType("cmmn");verify(query).scopeId("case-1");verify(query).scopeDefinitionId("case-def");verify(query).subScopeId("pi-primary");
        }
    }
    @Test void historical_handoff_keeps_terminal_time_and_a_read_only_domain_when_runtime_rows_are_gone() throws Exception {
        try(var f=new Fixture()){
            f.historyItem();
            var result=assertDoesNotThrow(()->f.tx.execute(s->f.access.observe(CaseSnapshotTest.origin())));
            assertNotNull(result);assertEquals("completed",result.state());assertEquals(CaseSnapshotTest.NOW,result.finishedAt());
            assertEquals(1,result.items().size());assertTrue(result.items().getFirst().historical());assertEquals(0,result.items().getFirst().revision());
            assertEquals(CaseSnapshotTest.NOW,result.items().getFirst().completedAt());verify(f.planQuery).includeEnded();
            verify(f.historyQuery).planItemInstanceCaseInstanceId("case-1");verify(f.historyQuery).planItemInstanceCaseDefinitionId("case-def");verify(f.historyQuery).planItemInstanceTenantId("20");
        }
    }
    private static CaseContext context(){return CaseSnapshotTest.context(CaseSnapshotTest.OWNER,java.util.Set.of());}
    private static final class Fixture implements AutoCloseable{
        final DataSource source=mock(DataSource.class); final Connection connection=mock(Connection.class); final SpringCmmnEngineConfiguration engine=mock(SpringCmmnEngineConfiguration.class);
        final CmmnRuntimeService runtime=mock(CmmnRuntimeService.class,RETURNS_DEEP_STUBS); final CmmnHistoryService history=mock(CmmnHistoryService.class,RETURNS_DEEP_STUBS);final CmmnTaskService tasks=mock(CmmnTaskService.class);
        final CaseInstanceQuery caseQuery=mock(CaseInstanceQuery.class,RETURNS_SELF);final HistoricCaseInstanceQuery caseHistoryQuery=mock(HistoricCaseInstanceQuery.class,RETURNS_SELF);
        final PlanItemInstanceQuery planQuery=mock(PlanItemInstanceQuery.class,RETURNS_SELF);final HistoricPlanItemInstanceQuery historyQuery=mock(HistoricPlanItemInstanceQuery.class,RETURNS_SELF);
        final AtomicReference<JdbcTemplate> jdbc=new AtomicReference<>();final org.mockito.MockedConstruction<JdbcTemplate> statements;final TransactionTemplate tx;final CaseNativeAccess access;
        Fixture()throws Exception{
            when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
            var manager=new DataSourceTransactionManager(source);when(engine.getDataSource()).thenReturn(source);when(engine.getTransactionManager()).thenReturn(manager);when(engine.getDatabaseSchema()).thenReturn("tianji_process");
            when(runtime.createCaseInstanceQuery()).thenReturn(caseQuery);when(runtime.createPlanItemInstanceQuery()).thenReturn(planQuery);when(history.createHistoricCaseInstanceQuery()).thenReturn(caseHistoryQuery);when(history.createHistoricPlanItemInstanceQuery()).thenReturn(historyQuery);
            when(planQuery.listPage(0,17)).thenReturn(List.of());when(historyQuery.listPage(0,17)).thenReturn(List.of());
            when(engine.getCmmnRuntimeService()).thenReturn(runtime);when(engine.getCmmnHistoryService()).thenReturn(history);when(engine.getCmmnTaskService()).thenReturn(tasks);
            statements=mockConstruction(JdbcTemplate.class,(value,ignored)->{jdbc.set(value);when(value.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"),eq(Boolean.class))).thenReturn(true);when(value.queryForObject(eq("SELECT clock_timestamp()"),eq(Timestamp.class))).thenReturn(Timestamp.from(CaseSnapshotTest.NOW));when(value.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(List.of("pi-primary"));});
            tx=new TransactionTemplate(manager);access=new CaseNativeAccess(source,engine);
        }
        PlanItemInstanceEntity runtimeItem(){
            var caze=mock(CaseInstance.class);when(caze.getId()).thenReturn("case-1");when(caze.getCaseDefinitionId()).thenReturn("case-def");when(caze.getTenantId()).thenReturn("20");when(caze.getBusinessKey()).thenReturn("business");when(caze.getState()).thenReturn("active");when(caseQuery.singleResult()).thenReturn(caze);
            var item=mock(PlanItemInstanceEntity.class);when(item.getId()).thenReturn("pi-primary");when(item.getCaseInstanceId()).thenReturn("case-1");when(item.getCaseDefinitionId()).thenReturn("case-def");when(item.getTenantId()).thenReturn("20");when(item.getElementId()).thenReturn("primary");when(item.getPlanItemDefinitionId()).thenReturn("primaryTask");when(item.getPlanItemDefinitionType()).thenReturn(org.flowable.cmmn.api.runtime.PlanItemDefinitionType.HUMAN_TASK);when(item.getState()).thenReturn("enabled");when(item.getRevision()).thenReturn(1);when(item.getCreateTime()).thenReturn(Date.from(CaseSnapshotTest.NOW));when(item.getLastEnabledTime()).thenReturn(Date.from(CaseSnapshotTest.NOW));when(planQuery.listPage(0,17)).thenReturn(List.of(item));return item;
        }
        void historyItem(){
            var caze=mock(HistoricCaseInstance.class);when(caze.getId()).thenReturn("case-1");when(caze.getCaseDefinitionId()).thenReturn("case-def");when(caze.getTenantId()).thenReturn("20");when(caze.getBusinessKey()).thenReturn("business");when(caze.getState()).thenReturn("completed");when(caze.getEndTime()).thenReturn(Date.from(CaseSnapshotTest.NOW));when(caseHistoryQuery.singleResult()).thenReturn(caze);
            var item=mock(HistoricPlanItemInstance.class);when(item.getId()).thenReturn("pi-primary");when(item.getCaseInstanceId()).thenReturn("case-1");when(item.getCaseDefinitionId()).thenReturn("case-def");when(item.getTenantId()).thenReturn("20");when(item.getElementId()).thenReturn("primary");when(item.getPlanItemDefinitionId()).thenReturn("primaryTask");when(item.getPlanItemDefinitionType()).thenReturn(org.flowable.cmmn.api.runtime.PlanItemDefinitionType.HUMAN_TASK);when(item.getState()).thenReturn("completed");when(item.getCreateTime()).thenReturn(Date.from(CaseSnapshotTest.NOW));when(item.getCompletedTime()).thenReturn(Date.from(CaseSnapshotTest.NOW));when(item.getEndedTime()).thenReturn(Date.from(CaseSnapshotTest.NOW));when(historyQuery.listPage(0,17)).thenReturn(List.of(item));
        }
        @Override public void close(){statements.close();}
    }
}
