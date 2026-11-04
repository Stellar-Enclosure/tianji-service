package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.api.runtime.CaseInstanceBuilder;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.*;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
import top.egon.cola.platform.tianji.process.server.release.*;
import top.egon.cola.platform.tianji.process.server.task.PinnedCaseActivityBehaviorFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowableCaseServiceTest {
    @Test void direct_start_pins_native_definition_and_atomically_preserves_owner_equals_executor() throws Exception {
        try(var f=new Fixture(true)){
            var result=assertDoesNotThrow(()->f.service.start(context("start"),new StartCaseCommand("governedCase","release","business","start","correlation")));
            assertNotNull(result);assertEquals("case-1",result.caseInstanceId());
            verify(f.builder).caseDefinitionId("case-def");verify(f.builder).tenantId("20");verify(f.builder).businessKey("business");verify(f.builder,never()).callbackId(anyString());
            verify(f.provenance).insert(argThat(seed->seed.owner().equals(CaseSnapshotTest.OWNER)&&seed.executor().equals(seed.owner())&&"DIRECT".equals(seed.startKind())&&"CASE_START".equals(seed.ownerStartCommandType())));
            var order=inOrder(f.commands.get(),f.provenance);order.verify(f.commands.get()).apply(CaseSnapshotTest.OWNER,ProcessCommandReceiptStore.Kind.CASE_START,"start","case-1",null);order.verify(f.provenance).insert(any());
        }
    }
    @Test void stopped_new_starts_still_allow_an_applied_start_replay_without_native_start() throws Exception {
        try(var f=new Fixture(false)){
            f.replayStart();var dto=assertDoesNotThrow(()->f.service.start(context("start"),new StartCaseCommand("governedCase","release","business","start","correlation")));
            assertNotNull(dto);verify(f.builder,never()).start();verify(f.provenance,never()).insert(any());
            var order=inOrder(f.provenance,f.nativeAccess.get());order.verify(f.provenance).lockScope(20,"case-1");order.verify(f.nativeAccess.get()).observe(any());
        }
    }
    @Test void a_fresh_start_is_denied_when_stopped_and_a_replayed_different_hash_conflicts() throws Exception {
        try(var f=new Fixture(false)){
            assertThrows(IllegalStateException.class,()->f.service.start(context("start"),new StartCaseCommand("governedCase","release","business","start","correlation")));
            f.replayStart();when(f.commands.get().lockIfPresent(any(),eq(ProcessCommandReceiptStore.Kind.CASE_START),eq("start"))).thenReturn(Optional.of(new ProcessCommandReceiptStore.Receipt("f".repeat(64),"release","correlation","APPLIED","case-1",CaseSnapshotTest.NOW,null)));
            assertThrows(ProcessConflictException.class,()->f.service.start(context("start"),new StartCaseCommand("governedCase","release","business","start","correlation")));
        }
    }
    @Test void reads_use_safe_provenance_and_terminal_native_history_without_impersonating_the_owner() throws Exception {
        try(var f=new Fixture(true)){
            when(f.nativeAccess.get().observe(any())).thenReturn(CaseSnapshotTest.observation("completed",CaseSnapshotTest.NOW,List.of()));
            var dto=assertDoesNotThrow(()->f.service.get(context("get"),new CaseInstanceQuery("case-1")));
            assertNotNull(dto);assertEquals(CaseInstanceStatusEnum.COMPLETED,dto.status());verify(f.provenance).find(20,"case-1");
        }
    }
    @Test void mutations_take_command_then_shared_scope_before_native_fence_and_replay_the_stored_twelve_fields() throws Exception {
        try(var f=new Fixture(true)){
            var item=CaseSnapshotTest.item("primary","enabled",1,false);var after=CaseSnapshotTest.item("primary","active",2,false);
            when(f.nativeAccess.get().observe(any())).thenReturn(CaseSnapshotTest.observation("active",null,List.of(item)),CaseSnapshotTest.observation("active",null,List.of(item)),CaseSnapshotTest.observation("active",null,List.of(after)));
            assertDoesNotThrow(()->{
                var cmd=new ActOnPlanItemCommand("act","case-1","pi-primary",CaseSnapshotTest.ENABLED_VERSION,CasePlanItemActionEnum.START);
                var result=f.service.actOnPlanItem(context("actPlanItem"),cmd);assertNotNull(result);assertEquals(CasePlanItemStateEnum.ACTIVE,result.resultingState());
            });
            var order=inOrder(f.provenance,f.nativeAccess.get());order.verify(f.provenance).lockScope(20,"case-1");order.verify(f.nativeAccess.get()).lockPlanItem(any(),eq("pi-primary"));
            verify(f.receipts).insert(any(),any());verify(f.runtime).startPlanItemInstance("pi-primary");
        }
    }
    @Test void applied_plan_replay_checks_current_released_lane_under_scope_even_after_native_rows_disappear() throws Exception {
        try(var f=new Fixture(true)){
            var command=new ActOnPlanItemCommand("act","case-1","pi-primary",CaseSnapshotTest.ENABLED_VERSION,CasePlanItemActionEnum.START);
            String hash=ProcessRequestDigest.hash(List.of("TIANJI_CASE_PLAN_ITEM_COMMAND_V1","START","case-1","pi-primary",command.expectedVersion()),Map.of());
            var result=new CaseOperationResult("act","case-1","pi-primary","primaryTask",CasePlanItemActionEnum.START,"release","governedCase",CasePlanItemStateEnum.ENABLED,command.expectedVersion(),CasePlanItemStateEnum.ACTIVE,"b".repeat(64),CaseSnapshotTest.NOW);
            var entry=new CasePlanItemReceiptStore.Entry(CaseSnapshotTest.OWNER,hash,result,CaseSnapshotTest.HASH,"case-def","7",2,3);
            when(f.commands.get().lockIfPresent(any(),eq(ProcessCommandReceiptStore.Kind.CASE_PLAN_ITEM),eq("act"))).thenReturn(Optional.of(new ProcessCommandReceiptStore.Receipt(hash,"release","correlation","APPLIED","pi-primary",CaseSnapshotTest.NOW,null)));
            when(f.receipts.find(any(),eq("act"),eq(hash))).thenReturn(Optional.of(entry));
            var refreshed=new CaseContext(CaseSnapshotTest.OWNER,"tianji.api.process.case.actPlanItem",Set.of("8"),CaseSnapshotTest.NOW.plusSeconds(30),5,6);
            assertEquals(result,assertDoesNotThrow(()->f.service.actOnPlanItem(refreshed,command)));
            verify(f.provenance).lockScope(20,"case-1");verify(f.nativeAccess.get(),never()).observe(any());verify(f.runtime,never()).startPlanItemInstance(anyString());
            var revoked=new CaseContext(CaseSnapshotTest.OWNER,refreshed.resourceKey(),Set.of("9"),refreshed.deadlineAt(),6,7);
            assertThrows(top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException.class,()->f.service.actOnPlanItem(revoked,command));
        }
    }
    @Test void old_versions_and_another_assignee_cannot_be_used_to_terminate_a_supplemental_task() throws Exception {
        try(var f=new Fixture(true)){
            var item=new CaseNativeAccess.PlanItem("pi-supplemental","supplemental","supplementalTask",org.flowable.cmmn.api.runtime.PlanItemDefinitionType.HUMAN_TASK,"active",false,1,CaseSnapshotTest.NOW,null,null,null,CaseSnapshotTest.NOW,null,null,null,null,"another-user",2);
            when(f.nativeAccess.get().observe(any())).thenReturn(CaseSnapshotTest.observation("active",null,List.of(item)));
            assertThrows(RuntimeException.class,()->f.service.actOnPlanItem(new CaseContext(CaseSnapshotTest.OWNER,"tianji.api.process.case.actPlanItem",Set.of("8"),CaseSnapshotTest.NOW.plusSeconds(30),2,3),new ActOnPlanItemCommand("act","case-1","pi-supplemental","a".repeat(64),CasePlanItemActionEnum.TERMINATE)));
            verify(f.runtime,never()).terminatePlanItemInstance(anyString());verify(f.receipts,never()).insert(any(),any());
        }
    }
    @Test void ambient_transactions_are_rejected_before_native_or_receipt_activity() throws Exception {
        try(var f=new Fixture(true)){
            assertThrows(IllegalStateException.class,()->f.tx.execute(s->f.service.get(context("get"),new CaseInstanceQuery("case-1"))));verify(f.provenance,never()).find(anyLong(),anyString());
        }
    }
    private static CaseContext context(String operation){return new CaseContext(CaseSnapshotTest.OWNER,"tianji.api.process.case."+operation,Set.of("7"),CaseSnapshotTest.NOW.plusSeconds(30),2,3);}
    private static final class Fixture implements AutoCloseable{
        final DataSource source=mock(DataSource.class);final Connection connection=mock(Connection.class);final SpringProcessEngineConfiguration process=mock(SpringProcessEngineConfiguration.class);final SpringCmmnEngineConfiguration cmmn=mock(SpringCmmnEngineConfiguration.class);final SpringDmnEngineConfiguration dmn=mock(SpringDmnEngineConfiguration.class);
        final CmmnRuntimeService runtime=mock(CmmnRuntimeService.class);final CaseInstanceBuilder builder=mock(CaseInstanceBuilder.class,RETURNS_SELF);final CaseStartProvenanceStore provenance=mock(CaseStartProvenanceStore.class);final CasePlanItemReceiptStore receipts=mock(CasePlanItemReceiptStore.class);
        final AtomicReference<ProcessCommandReceiptStore> commands=new AtomicReference<>();final AtomicReference<CaseNativeAccess> nativeAccess=new AtomicReference<>();
        final org.mockito.MockedConstruction<JdbcTemplate> statements;final org.mockito.MockedConstruction<ProcessReleaseService> releases;final org.mockito.MockedConstruction<VerifiedProcessModels> models;final org.mockito.MockedConstruction<ProcessCommandReceiptStore> commandMocks;final org.mockito.MockedConstruction<CaseNativeAccess> nativeMocks;final org.mockito.MockedStatic<PinnedCaseActivityBehaviorFactory> readiness;
        final FlowableCaseService service;final TransactionTemplate tx;
        Fixture(boolean starts)throws Exception{
            when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);var manager=new DataSourceTransactionManager(source);tx=new TransactionTemplate(manager);
            for(var engine:List.of(process,cmmn,dmn)){engine.setDataSource(source);engine.setDatabaseSchema("tianji_process");}
            when(process.getDataSource()).thenReturn(source);when(process.getTransactionManager()).thenReturn(manager);when(process.getDatabaseSchema()).thenReturn("tianji_process");when(cmmn.getDataSource()).thenReturn(source);when(cmmn.getTransactionManager()).thenReturn(manager);when(cmmn.getDatabaseSchema()).thenReturn("tianji_process");when(dmn.getDataSource()).thenReturn(source);when(dmn.getTransactionManager()).thenReturn(manager);when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
            when(cmmn.getCmmnRuntimeService()).thenReturn(runtime);when(runtime.createCaseInstanceBuilder()).thenReturn(builder);
            var instance=mock(CaseInstance.class);when(instance.getId()).thenReturn("case-1");when(instance.getCaseDefinitionId()).thenReturn("case-def");when(instance.getTenantId()).thenReturn("20");when(instance.getBusinessKey()).thenReturn("business");when(builder.start()).thenReturn(instance);
            when(provenance.usesDataSource(source)).thenReturn(true);when(receipts.usesDataSource(source)).thenReturn(true);when(provenance.find(20,"case-1")).thenReturn(Optional.of(CaseSnapshotTest.origin()));when(provenance.byExecutor(any(),anyString())).thenReturn(Optional.empty());when(provenance.insert(any())).thenAnswer(call->new CaseStartProvenanceStore.Entry(call.getArgument(0),CaseSnapshotTest.NOW));
            var definition=new ProcessReleaseService.Definition(ValidatedProcessModel.Type.CMMN,"governedCase","case-def","deployment",1,CaseSnapshotTest.HASH,Map.of());
            statements=mockConstruction(JdbcTemplate.class,(value,ignored)->{});
            releases=mockConstruction(ProcessReleaseService.class,(value,ignored)->when(value.get(any(),eq("release"))).thenReturn(new ProcessReleaseService.Release("release","a".repeat(64),CaseSnapshotTest.NOW,List.of(definition))));
            models=mockConstruction(VerifiedProcessModels.class,(value,ignored)->when(value.caseModel(20,definition)).thenReturn(CaseSnapshotTest.model()));
            commandMocks=mockConstruction(ProcessCommandReceiptStore.class,(value,ignored)->{commands.set(value);when(value.lockIfPresent(any(),any(),anyString())).thenReturn(Optional.empty());when(value.insert(any(),any(),anyString(),anyString(),anyString(),anyString())).thenReturn(true);});
            nativeMocks=mockConstruction(CaseNativeAccess.class,(value,ignored)->{nativeAccess.set(value);when(value.current(any())).thenReturn(CaseSnapshotTest.NOW);when(value.observe(any())).thenReturn(CaseSnapshotTest.observation("active",null,List.of()));});
            readiness=mockStatic(PinnedCaseActivityBehaviorFactory.class);service=new FlowableCaseService(process,cmmn,dmn,new ProcessModelValidator(),provenance,receipts,starts);
        }
        void replayStart(){String hash=ProcessRequestDigest.hash(List.of("TIANJI_CASE_START_V1","release","governedCase","business","correlation"),Map.of());when(commands.get().lockIfPresent(any(),eq(ProcessCommandReceiptStore.Kind.CASE_START),eq("start"))).thenReturn(Optional.of(new ProcessCommandReceiptStore.Receipt(hash,"release","correlation","APPLIED","case-1",CaseSnapshotTest.NOW,null)));var old=CaseSnapshotTest.origin().seed();var seed=new CaseStartProvenanceStore.Seed(old.caseInstanceId(),old.startKind(),old.businessKey(),old.correlationId(),old.releaseKey(),old.caseDefinitionKey(),old.caseEngineDefinitionId(),old.caseModelSha256(),old.owner(),old.ownerStartCommandType(),old.ownerStartCommandId(),hash,old.executor(),old.caseStartCommandId(),hash,old.sourceId(),old.parentProcessInstanceId(),old.sourceOccurrenceId(),old.sourceElementId(),old.parentDefinitionKey(),old.parentEngineDefinitionId(),old.parentModelSha256());when(provenance.byExecutor(CaseSnapshotTest.OWNER,"start")).thenReturn(Optional.of(new CaseStartProvenanceStore.Entry(seed,CaseSnapshotTest.NOW)));}
        @Override public void close(){readiness.close();nativeMocks.close();commandMocks.close();models.close();releases.close();statements.close();}
    }
}
