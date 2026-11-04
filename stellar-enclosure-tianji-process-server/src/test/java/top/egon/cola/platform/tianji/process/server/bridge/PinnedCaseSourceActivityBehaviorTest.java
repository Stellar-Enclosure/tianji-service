package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.CaseServiceTask;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.impl.bpmn.behavior.CaseTaskActivityBehavior;
import org.flowable.engine.impl.persistence.entity.ActivityInstanceEntity;
import org.flowable.engine.impl.persistence.entity.ActivityInstanceEntityManager;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.*;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PinnedCaseSourceActivityBehaviorTest {
    @Test void real_converter_case_task_uses_the_pinned_native_hook_without_eager_service_lookup() throws Exception {
        try(var input=getClass().getResourceAsStream("/fixtures/formal-case-linked.bpmn20.xml")){
            assertNotNull(input);byte[] bytes=input.readAllBytes();
            var model=new BpmnXMLConverter().convertToBpmnModel(()->new ByteArrayInputStream(bytes),true,true);
            var task=assertInstanceOf(CaseServiceTask.class,model.getMainProcess().getFlowElement("caseSource"));
            var factory=new PinnedDecisionActivityBehaviorFactory(()->{throw new AssertionError("eager service");},()->{throw new AssertionError("eager registry");});
            assertInstanceOf(PinnedCaseSourceActivityBehavior.class,factory.createCaseTaskBehavior(task));
            assertInstanceOf(PinnedCaseSourceActivityBehavior.class,new PinnedDecisionActivityBehaviorFactory().createCaseTaskBehavior(task));
        }
    }
    @Test void source_readiness_requires_both_lazy_suppliers_and_the_actual_engine_parser_factory_identity() {
        var process=mock(SpringProcessEngineConfiguration.class);var parser=mock(org.flowable.engine.impl.bpmn.parser.BpmnParser.class);
        when(process.getBpmnParser()).thenReturn(parser);
        var noSource=new PinnedDecisionActivityBehaviorFactory();when(process.getActivityBehaviorFactory()).thenReturn(noSource);when(parser.getActivityBehaviorFactory()).thenReturn(noSource);
        assertThrows(IllegalStateException.class,()->PinnedDecisionActivityBehaviorFactory.requireCaseSourcesConfigured(process));
        var factory=new PinnedDecisionActivityBehaviorFactory(()->{throw new AssertionError("service resolved during readiness");},()->{throw new AssertionError("registry resolved during readiness");});
        when(process.getActivityBehaviorFactory()).thenReturn(factory);when(parser.getActivityBehaviorFactory()).thenReturn(factory);
        assertDoesNotThrow(()->PinnedDecisionActivityBehaviorFactory.requireCaseSourcesConfigured(process));
        when(parser.getActivityBehaviorFactory()).thenReturn(noSource);assertThrows(IllegalStateException.class,()->PinnedDecisionActivityBehaviorFactory.requireCaseSourcesConfigured(process));
    }
    @Test void actual_unfinished_activity_identity_is_passed_to_application_then_only_validated_leave_runs() throws Exception {
        try(var f=new Fixture()){
            assertDoesNotThrow(()->f.behavior.execute(f.execution));
            verify(f.service).startLinked(eq(CaseSourceRegistryTest.canonical()),eq(new FlowableCaseService.NativeSourceFacts("parent","parent-def","caseSource","native-activity","source-execution")));
            var order=inOrder(f.service,f.behavior);order.verify(f.service).startLinked(any(),any());order.verify(f.behavior).leave(f.execution);
        }
    }
    @Test void missing_native_context_or_activity_cache_proof_cannot_invent_an_occurrence() throws Exception {
        try(var f=new Fixture()){
            f.contexts.when(Context::getCommandContext).thenReturn(null);assertThrows(IllegalStateException.class,()->f.behavior.execute(f.execution));
            verifyNoInteractions(f.service);verify(f.behavior,never()).leave(any());
        }
    }
    @Test void mismatched_or_already_ended_source_activity_is_rejected_before_case_start() throws Exception {
        try(var f=new Fixture()){
            when(f.activity.getExecutionId()).thenReturn("other");assertThrows(IllegalStateException.class,()->f.behavior.execute(f.execution));
            when(f.activity.getExecutionId()).thenReturn("source-execution");when(f.activity.getEndTime()).thenReturn(java.util.Date.from(Instant.parse("2026-11-04T08:00:00Z")));
            assertThrows(IllegalStateException.class,()->f.behavior.execute(f.execution));verifyNoInteractions(f.service);verify(f.behavior,never()).leave(any());
        }
    }
    @Test void a_missing_registration_fails_closed_without_the_native_default_callback_or_leave() throws Exception {
        try(var f=new Fixture()){
            when(f.registry.require(any())).thenThrow(new IllegalStateException("source unavailable"));
            assertThrows(IllegalStateException.class,()->f.behavior.execute(f.execution));verifyNoInteractions(f.service);verify(f.behavior,never()).leave(any());
        }
    }
    private static final class Fixture implements AutoCloseable{
        final SpringProcessEngineConfiguration process=mock(SpringProcessEngineConfiguration.class);final SpringDmnEngineConfiguration dmn=mock(SpringDmnEngineConfiguration.class);final CommandContext command=mock(CommandContext.class);final ExecutionEntity execution=mock(ExecutionEntity.class);final ActivityInstanceEntity activity=mock(ActivityInstanceEntity.class);final ActivityInstanceEntityManager activities=mock(ActivityInstanceEntityManager.class);
        final FlowableCaseService service=mock(FlowableCaseService.class);final CaseSourceRegistry registry=mock(CaseSourceRegistry.class);final PinnedCaseSourceActivityBehavior behavior;
        final org.mockito.MockedStatic<Context> contexts;final org.mockito.MockedStatic<CommandContextUtil> nativeContexts;final org.mockito.MockedConstruction<ProcessReleaseService> releases;final org.mockito.MockedConstruction<VerifiedProcessModels> models;
        Fixture(){
            var task=new CaseServiceTask();task.setId("caseSource");when(execution.getId()).thenReturn("source-execution");when(execution.getProcessInstanceId()).thenReturn("parent");when(execution.getProcessDefinitionId()).thenReturn("parent-def");when(execution.getTenantId()).thenReturn("20");when(execution.getCurrentActivityId()).thenReturn("caseSource");when(execution.getCurrentFlowElement()).thenReturn(task);
            when(activity.getId()).thenReturn("native-activity");when(activity.getExecutionId()).thenReturn("source-execution");when(activity.getProcessInstanceId()).thenReturn("parent");when(activity.getProcessDefinitionId()).thenReturn("parent-def");when(activity.getActivityId()).thenReturn("caseSource");when(activity.getTenantId()).thenReturn("20");when(activities.findUnfinishedActivityInstance(execution)).thenReturn(activity);
            contexts=mockStatic(Context.class);contexts.when(Context::getCommandContext).thenReturn(command);nativeContexts=mockStatic(CommandContextUtil.class);nativeContexts.when(CommandContextUtil::getProcessEngineConfiguration).thenReturn(process);nativeContexts.when(CommandContextUtil::getDmnEngineConfiguration).thenReturn(dmn);nativeContexts.when(()->CommandContextUtil.getActivityInstanceEntityManager(command)).thenReturn(activities);
            var parent=new ProcessReleaseService.Definition(ValidatedProcessModel.Type.BPMN,"process","parent-def","parent-deploy",1,CaseSourceRegistryTest.PARENT_SHA,Map.of());var caze=new ProcessReleaseService.Definition(ValidatedProcessModel.Type.CMMN,"governedCase","case-def","case-deploy",1,CaseSourceRegistryTest.CASE_SHA,Map.of());
            releases=mockConstruction(ProcessReleaseService.class,(value,ignored)->when(value.getForProcessDefinition(20,"parent-def")).thenReturn(new ProcessReleaseService.Release("release","c".repeat(64),Instant.parse("2026-11-04T08:00:00Z"),List.of(parent,caze))));
            var model=mock(ValidatedProcessModel.class);when(model.caseSources()).thenReturn(Map.of("caseSource",new ValidatedProcessModel.CaseSource("governedCase","waitCaseResult","TianjiCaseCompletedV1","waitCaseTimeout",Duration.ofMinutes(5))));
            models=mockConstruction(VerifiedProcessModels.class,(value,ignored)->when(value.runningProcess(20,parent)).thenReturn(model));
            when(registry.require(CaseSourceRegistryTest.binding())).thenReturn(CaseSourceRegistryTest.canonical());when(registry.current(any())).thenReturn(true);
            var owner=ProcessActor.verified(20,ProcessActor.SubjectType.USER,"owner",CaseSourceRegistryTest.ISSUER.toASCIIString());var registered=CaseSourceRegistryTest.canonical();
            var seed=new CaseStartProvenanceStore.Seed("case-1","LINKED_BPMN","business","correlation","release","governedCase","case-def",CaseSourceRegistryTest.CASE_SHA,owner,"START","parent-start","d".repeat(64),registered.actor(),"e".repeat(64),"f".repeat(64),"source","parent","native-activity","caseSource","process","parent-def",CaseSourceRegistryTest.PARENT_SHA);
            when(service.startLinked(any(),any())).thenReturn(new CaseStartProvenanceStore.Entry(seed,Instant.parse("2026-11-04T08:00:00Z")));
            behavior=spy(new PinnedCaseSourceActivityBehavior(task,()->service,()->registry));doNothing().when(behavior).leave(execution);
        }
        @Override public void close(){models.close();releases.close();nativeContexts.close();contexts.close();}
    }
}
