package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.cmmn.converter.CmmnXmlConverter;
import org.flowable.cmmn.engine.impl.behavior.impl.HumanTaskActivityBehavior;
import org.flowable.cmmn.engine.impl.persistence.entity.PlanItemInstanceEntity;
import org.flowable.cmmn.engine.impl.util.CommandContextUtil;
import org.flowable.cmmn.engine.interceptor.MigrationContext;
import org.flowable.cmmn.model.HumanTask;
import org.flowable.cmmn.model.PlanItem;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.interceptor.CommandContext;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedCaseModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PinnedCaseHumanTaskActivityBehaviorTest {
    @Test void factory_preserves_unmarked_native_tasks_and_selects_only_the_governed_marker() throws Exception {
        var service=mock(FlowableCaseService.class);var factory=new PinnedCaseActivityBehaviorFactory(()->service);
        var plain=new HumanTask();plain.setId("primaryTask");assertEquals(HumanTaskActivityBehavior.class,factory.createHumanTaskActivityBehavior(new PlanItem(),plain).getClass());
        var task=task();assertInstanceOf(PinnedCaseHumanTaskActivityBehavior.class,factory.createHumanTaskActivityBehavior(new PlanItem(),task));verifyNoInteractions(service);
    }
    @Test void a_same_named_extension_in_an_unrelated_namespace_is_unmarked_legacy() throws Exception {
        var task=task();assertNull(task.getAttributeValue("urn:stellar-enclosure:tianji:task:1","kind"));
        var lane=task.getExtensionElements().get("taskLane").getFirst();assertEquals("urn:stellar-enclosure:tianji:task:1",lane.getNamespace());
        lane.setNamespace("urn:unrelated:task:1");
        var factory=new PinnedCaseActivityBehaviorFactory(()->null);
        assertEquals(HumanTaskActivityBehavior.class,factory.createHumanTaskActivityBehavior(new PlanItem(),task).getClass());
    }
    @Test void readiness_is_not_satisfied_by_an_uninstalled_or_uninitialized_factory() {
        assertThrows(IllegalStateException.class,()->PinnedCaseActivityBehaviorFactory.requireConfigured(mock(SpringCmmnEngineConfiguration.class)));
    }
    @Test void migration_and_raw_assignment_are_rejected_before_native_superclass_work() throws Exception {
        var model=task();var service=mock(FlowableCaseService.class);var behavior=new PinnedCaseHumanTaskActivityBehavior(model,()->service);var context=mock(CommandContext.class);var item=mock(PlanItemInstanceEntity.class);
        assertThrows(IllegalStateException.class,()->behavior.execute(context,item,mock(MigrationContext.class)));verifyNoInteractions(service);
        model.setAssignee("${untrusted}");assertThrows(IllegalStateException.class,()->behavior.execute(context,item,null));verifyNoInteractions(service);
    }
    @Test void fixed_candidate_groups_come_from_released_metadata_without_expression_or_raw_claims() throws Exception {
        try(var f=new Fixture()){
            f.behavior.handleCandidateGroups(f.command,f.item,null,f.task,null,null);
            verify(f.task).addCandidateGroups(List.of("role:7","role:8"));verify(f.service).nativeTaskPolicy(f.item);
            verify(f.task,never()).setAssignee(anyString());verify(f.task,never()).setOwner(anyString());
        }
    }
    @Test void candidate_hook_rejects_foreign_native_task_scope_before_creating_identity_links() throws Exception {
        try(var f=new Fixture()){
            when(f.task.getScopeId()).thenReturn("foreign-case");
            assertThrows(IllegalStateException.class,()->f.behavior.handleCandidateGroups(f.command,f.item,null,f.task,null,null));
            verify(f.task,never()).addCandidateGroups(anyCollection());
        }
    }
    private static HumanTask task()throws Exception{
        try(var input=PinnedCaseHumanTaskActivityBehaviorTest.class.getResourceAsStream("/fixtures/governed-case.cmmn")){
            assertNotNull(input);byte[] bytes=input.readAllBytes();return (HumanTask)new CmmnXmlConverter().convertToCmmnModel(()->new ByteArrayInputStream(bytes),true,true).findPlanItemDefinition("primaryTask");
        }
    }
    private static final class Fixture implements AutoCloseable{
        final CommandContext command=mock(CommandContext.class);final SpringCmmnEngineConfiguration config=mock(SpringCmmnEngineConfiguration.class);final PlanItemInstanceEntity item=mock(PlanItemInstanceEntity.class);final TaskEntity task=mock(TaskEntity.class);final FlowableCaseService service=mock(FlowableCaseService.class);final PinnedCaseHumanTaskActivityBehavior behavior;
        final org.mockito.MockedStatic<Context> contexts;final org.mockito.MockedStatic<CommandContextUtil> cmmnContexts;
        Fixture()throws Exception{
            when(item.getTenantId()).thenReturn("20");when(item.getId()).thenReturn("pi-primary");when(item.getCaseInstanceId()).thenReturn("case-1");when(item.getCaseDefinitionId()).thenReturn("case-def");when(item.getElementId()).thenReturn("primary");when(item.getPlanItemDefinitionId()).thenReturn("primaryTask");
            when(task.getTenantId()).thenReturn("20");when(task.getScopeType()).thenReturn("cmmn");when(task.getScopeId()).thenReturn("case-1");when(task.getSubScopeId()).thenReturn("pi-primary");when(task.getScopeDefinitionId()).thenReturn("case-def");when(task.getTaskDefinitionKey()).thenReturn("primaryTask");
            var policy=new ValidatedProcessModel.HumanTask(ValidatedProcessModel.HumanTaskKind.ACTION,Map.of("operator",Set.of("7","8")),"operator",null,null,List.of());
            when(service.nativeTaskPolicy(item)).thenReturn(new ValidatedCaseModel("governedCase",new byte[]{1},Map.of("primaryTask",policy),Map.of("primary",new ValidatedCaseModel.PlanItemPolicy("primaryTask",Set.of(ValidatedCaseModel.Action.START),Set.of("7","8")))));
            contexts=mockStatic(Context.class);contexts.when(Context::getCommandContext).thenReturn(command);cmmnContexts=mockStatic(CommandContextUtil.class);cmmnContexts.when(()->CommandContextUtil.getCmmnEngineConfiguration(command)).thenReturn(config);
            behavior=new PinnedCaseHumanTaskActivityBehavior(task(),()->service);
        }
        @Override public void close(){cmmnContexts.close();contexts.close();}
    }
}
