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
    @Test void linked_start_inherits_the_applied_parent_owner_and_uses_the_fixed_service_executor_in_one_transaction() throws Exception {
        try(var f=new LinkedFixture()){
            var before=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            var result=assertDoesNotThrow(()->f.base.tx.execute(status->f.service.startLinked(f.registration,f.facts)));
            assertNotNull(result);assertEquals(CaseSnapshotTest.OWNER,result.seed().owner());assertEquals(f.registration.actor(),result.seed().executor());
            assertNotEquals(result.seed().owner().issuerFingerprint(),result.seed().executor().issuerFingerprint());assertEquals("START",result.seed().ownerStartCommandType());assertEquals("parent-start",result.seed().ownerStartCommandId());
            assertEquals("business",result.seed().businessKey());assertEquals("correlation",result.seed().correlationId());assertEquals("release",result.seed().releaseKey());assertEquals("native-occurrence",result.seed().sourceOccurrenceId());
            assertSame(before,org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            var order=inOrder(f.base.builder,f.base.commands.get(),f.base.provenance,f.inbox);order.verify(f.base.builder).start();order.verify(f.base.commands.get()).apply(eq(f.registration.actor()),eq(ProcessCommandReceiptStore.Kind.CASE_START),anyString(),eq("case-1"),isNull());order.verify(f.base.provenance).insert(any());order.verify(f.inbox).prepare(any());
        }
    }
    @Test void linked_start_requires_actual_native_context_an_applied_parent_and_running_completion_before_creating_a_case() throws Exception {
        try(var f=new LinkedFixture()){
            assertThrows(IllegalStateException.class,()->f.service.startLinked(f.registration,f.facts));
            f.parent.put("state","PENDING");assertThrows(IllegalStateException.class,()->f.base.tx.execute(status->f.service.startLinked(f.registration,f.facts)));
            f.parent.put("state","APPLIED");f.running.set(false);assertThrows(IllegalStateException.class,()->f.base.tx.execute(status->f.service.startLinked(f.registration,f.facts)));
            verify(f.base.builder,never()).start();verify(f.inbox,never()).prepare(any());
        }
    }
    @Test void repeated_native_occurrence_ignores_a_source_audit_rename_without_starting_another_case() throws Exception {
        try(var f=new LinkedFixture()){
            var first=assertDoesNotThrow(()->f.base.tx.execute(status->f.service.startLinked(f.registration,f.facts)));assertNotNull(first);
            when(f.base.provenance.byLinkedOccurrence(20,"parent","native-occurrence")).thenReturn(Optional.of(first));
            when(f.base.commands.get().lockIfPresent(eq(f.registration.actor()),eq(ProcessCommandReceiptStore.Kind.CASE_START),eq(first.seed().caseStartCommandId()))).thenReturn(Optional.of(new ProcessCommandReceiptStore.Receipt(first.seed().caseStartRequestSha256(),"release","correlation","APPLIED","case-1",CaseSnapshotTest.NOW,null)));
            var renamed=new top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.Registration(f.registration.binding(),"renamed",f.registration.actor(),f.registration.authorizationKey());
            f.running.set(false);
            assertEquals(first,assertDoesNotThrow(()->f.base.tx.execute(status->f.service.startLinked(renamed,f.facts))));
            verify(f.base.builder,times(1)).start();verify(f.inbox,times(1)).prepare(any());
        }
    }
    @Test void forged_native_occurrence_or_cross_release_source_binding_cannot_create_a_case() throws Exception {
        try(var f=new LinkedFixture()){
            when(f.activity.getId()).thenReturn("another-occurrence");assertThrows(IllegalStateException.class,()->f.base.tx.execute(status->f.service.startLinked(f.registration,f.facts)));
            when(f.activity.getId()).thenReturn("native-occurrence");var old=f.registration.binding();
            var binding=new top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.BindingKey(20,"other-release",old.parentDefinitionKey(),old.parentModelSha256(),old.sourceElementId(),old.caseDefinitionKey(),old.caseModelSha256());
            var wrong=new top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.Registration(binding,"source",f.registration.actor(),f.registration.authorizationKey());
            assertThrows(IllegalStateException.class,()->f.base.tx.execute(status->f.service.startLinked(wrong,f.facts)));verify(f.base.builder,never()).start();
        }
    }
    @Test void failure_after_linked_native_start_rolls_back_the_same_parent_transaction_and_never_commits_a_partial_inbox() throws Exception {
        try(var f=new LinkedFixture()){
            when(f.inbox.prepare(any())).thenThrow(new IllegalStateException("inbox write failed"));
            assertThrows(IllegalStateException.class,()->f.base.tx.execute(status->f.service.startLinked(f.registration,f.facts)));
            verify(f.base.builder).start();verify(f.base.connection).rollback();
        }
    }
    private static CaseContext context(String operation){return new CaseContext(CaseSnapshotTest.OWNER,"tianji.api.process.case."+operation,Set.of("7"),CaseSnapshotTest.NOW.plusSeconds(30),2,3);}
    private static final class LinkedFixture implements AutoCloseable {
        final Fixture base=new Fixture(true);final top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry registry=mock(top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.class);final top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox inbox=mock(top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox.class);
        final java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean(true);final top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.Registration registration;final FlowableCaseService.NativeSourceFacts facts=new FlowableCaseService.NativeSourceFacts("parent","parent-def","caseSource","native-occurrence","source-execution");
        final org.flowable.common.engine.impl.interceptor.CommandContext command=mock(org.flowable.common.engine.impl.interceptor.CommandContext.class);final org.flowable.engine.impl.persistence.entity.ExecutionEntity execution=mock(org.flowable.engine.impl.persistence.entity.ExecutionEntity.class);final org.flowable.engine.impl.persistence.entity.ActivityInstanceEntity activity=mock(org.flowable.engine.impl.persistence.entity.ActivityInstanceEntity.class);
        final org.mockito.MockedStatic<org.flowable.common.engine.impl.context.Context> contexts;final org.mockito.MockedStatic<org.flowable.engine.impl.util.CommandContextUtil> nativeContexts;final FlowableCaseService service;final java.util.Map<String,Object> parent=new java.util.HashMap<>();
        LinkedFixture()throws Exception {
            var actor=top.egon.cola.platform.tianji.process.server.identity.ProcessActor.verified(20,top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.SERVICE,"case-service","https://service.example/20");
            var key=new top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey("https://service.example/20","local","case-service",1,"process-app",java.net.URI.create("https://process.example/api/tianji"),top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext.TENANT,"20",Set.of("tianji:case:start"));
            var binding=new top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.BindingKey(20,"release","process","b".repeat(64),"caseSource","governedCase",CaseSnapshotTest.HASH);registration=new top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry.Registration(binding,"source",actor,key);when(registry.current(any())).thenReturn(true);when(registry.require(any())).thenReturn(registration);
            when(base.provenance.byLinkedOccurrence(anyLong(),anyString(),anyString())).thenReturn(Optional.empty());
            service=new FlowableCaseService(base.process,base.cmmn,base.dmn,new ProcessModelValidator(),base.provenance,base.receipts,true,registry,inbox,running::get);
            var definition=new ProcessReleaseService.Definition(ValidatedProcessModel.Type.CMMN,"governedCase","case-def","deployment",1,CaseSnapshotTest.HASH,Map.of());var parentDefinition=new ProcessReleaseService.Definition(ValidatedProcessModel.Type.BPMN,"process","parent-def","parent-deployment",1,"b".repeat(64),Map.of());
            when(base.releases.constructed().getLast().get(any(),eq("release"))).thenReturn(new ProcessReleaseService.Release("release","a".repeat(64),CaseSnapshotTest.NOW,List.of(parentDefinition,definition)));
            var parentModel=mock(ValidatedProcessModel.class);when(parentModel.caseSources()).thenReturn(Map.of("caseSource",new ValidatedProcessModel.CaseSource("governedCase","waitCaseResult","TianjiCaseCompletedV1","waitCaseTimeout",java.time.Duration.ofMinutes(5))));when(base.models.constructed().getLast().runningProcess(20,parentDefinition)).thenReturn(parentModel);
            parent.putAll(Map.of("tenant_id",20L,"subject_type","USER","subject_issuer",CaseSnapshotTest.OWNER.issuerFingerprint(),"subject_id",CaseSnapshotTest.OWNER.subjectId(),"command_id","parent-start","request_sha256","d".repeat(64),"release_key","release","correlation_id","correlation","state","APPLIED","resource_id","parent"));parent.put("created_at",java.sql.Timestamp.from(CaseSnapshotTest.NOW.minusSeconds(10)));
            var jdbc=base.statements.constructed().getLast();when(jdbc.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"),eq(Boolean.class))).thenReturn(true);when(jdbc.queryForObject(eq("SELECT clock_timestamp()"),eq(java.sql.Timestamp.class))).thenReturn(java.sql.Timestamp.from(CaseSnapshotTest.NOW));
            when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),any(Object[].class))).thenAnswer(call->{var rs=mock(java.sql.ResultSet.class);when(rs.getString(anyString())).thenAnswer(read->{var value=parent.get(read.getArgument(0));return value==null?null:value.toString();});when(rs.getLong(anyString())).thenAnswer(read->((Number)parent.get(read.getArgument(0))).longValue());when(rs.getTimestamp(anyString())).thenAnswer(read->parent.get(read.getArgument(0)));org.springframework.jdbc.core.RowMapper<?> mapper=call.getArgument(1);return List.of(mapper.mapRow(rs,0));});
            var executions=mock(org.flowable.engine.impl.persistence.entity.ExecutionEntityManager.class);when(executions.findById("source-execution")).thenReturn(execution);var activities=mock(org.flowable.engine.impl.persistence.entity.ActivityInstanceEntityManager.class);when(activities.findUnfinishedActivityInstance(execution)).thenReturn(activity);
            var task=new org.flowable.bpmn.model.CaseServiceTask();task.setId("caseSource");when(execution.getId()).thenReturn("source-execution");when(execution.getProcessInstanceId()).thenReturn("parent");when(execution.getProcessDefinitionId()).thenReturn("parent-def");when(execution.getTenantId()).thenReturn("20");when(execution.getCurrentActivityId()).thenReturn("caseSource");when(execution.getCurrentFlowElement()).thenReturn(task);
            var root=mock(org.flowable.engine.impl.persistence.entity.ExecutionEntity.class);when(root.getId()).thenReturn("parent");when(root.getProcessDefinitionId()).thenReturn("parent-def");when(root.getTenantId()).thenReturn("20");when(root.getBusinessKey()).thenReturn("business");when(execution.getProcessInstance()).thenReturn(root);
            when(activity.getId()).thenReturn("native-occurrence");when(activity.getExecutionId()).thenReturn("source-execution");when(activity.getProcessInstanceId()).thenReturn("parent");when(activity.getProcessDefinitionId()).thenReturn("parent-def");when(activity.getActivityId()).thenReturn("caseSource");when(activity.getTenantId()).thenReturn("20");
            contexts=mockStatic(org.flowable.common.engine.impl.context.Context.class);contexts.when(org.flowable.common.engine.impl.context.Context::getCommandContext).thenReturn(command);nativeContexts=mockStatic(org.flowable.engine.impl.util.CommandContextUtil.class);nativeContexts.when(org.flowable.engine.impl.util.CommandContextUtil::getProcessEngineConfiguration).thenReturn(base.process);nativeContexts.when(()->org.flowable.engine.impl.util.CommandContextUtil.getExecutionEntityManager(command)).thenReturn(executions);nativeContexts.when(()->org.flowable.engine.impl.util.CommandContextUtil.getActivityInstanceEntityManager(command)).thenReturn(activities);
            var factory=new top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory(()->service,()->registry);var parser=mock(org.flowable.engine.impl.bpmn.parser.BpmnParser.class);when(base.process.getActivityBehaviorFactory()).thenReturn(factory);when(base.process.getBpmnParser()).thenReturn(parser);when(parser.getActivityBehaviorFactory()).thenReturn(factory);
        }
        @Override public void close(){nativeContexts.close();contexts.close();base.close();}
    }
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
