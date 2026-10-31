package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.el.ExpressionManager;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.service.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Fixed converter and current-command markers only; native task persistence remains PostgreSQL acceptance work. */
class PinnedHumanTaskActivityBehaviorTest {
    @Test void the_existing_factory_preserves_plain_user_tasks_and_selects_the_native_adapter_for_governed_xml() throws Exception {
        var factory = new PinnedDecisionActivityBehaviorFactory(); var plain = new UserTask(); plain.setId("plain");
        assertEquals(UserTaskActivityBehavior.class, factory.createUserTaskActivityBehavior(plain).getClass());
        try (var fixture = new Fixture(false, Map.of())) { assertInstanceOf(PinnedHumanTaskActivityBehavior.class, factory.createUserTaskActivityBehavior(fixture.userTask)); }
    }

    @Test void static_assignment_uses_only_the_fixed_role_lane_and_does_not_read_execution_variables_or_evaluate_expressions() throws Exception {
        try (var fixture = new Fixture(false, Map.of())) {
            fixture.assign(); verify(fixture.task).addCandidateGroups(List.of("role:50", "role:51"));
            verify(fixture.execution, never()).getVariable(anyString()); verifyNoInteractions(fixture.expressions, fixture.taskService);
            verify(fixture.task, never()).setAssignee(anyString()); verify(fixture.task, never()).setOwner(anyString());
        }
    }

    @Test void dynamic_assignment_requires_a_typed_pinned_dmn_result_and_has_no_missing_or_unknown_lane_fallback() throws Exception {
        try (var fixture = new Fixture(true, Map.of("amount", ValueType.INTEGER))) {
            when(fixture.execution.getVariable("queue")).thenReturn("manager"); fixture.assign(); verify(fixture.task).addCandidateGroups(List.of("role:60"));
            clearInvocations(fixture.task);
            when(fixture.execution.getVariable("queue")).thenReturn(null); assertThrows(IllegalStateException.class, fixture::assign);
            when(fixture.execution.getVariable("queue")).thenReturn(60L); assertThrows(IllegalStateException.class, fixture::assign);
            when(fixture.execution.getVariable("queue")).thenReturn("outside"); assertThrows(IllegalStateException.class, fixture::assign);
            verify(fixture.task, never()).addCandidateGroups(anyCollection()); verifyNoInteractions(fixture.expressions, fixture.taskService);
        }
    }

    @Test void a_selector_preseeded_by_start_inputs_is_rejected_before_creating_candidate_links() throws Exception {
        try (var fixture = new Fixture(true, Map.of("amount", ValueType.INTEGER, "queue", ValueType.STRING))) {
            when(fixture.execution.getVariable("queue")).thenReturn("finance"); assertThrows(IllegalArgumentException.class, fixture::assign);
            verify(fixture.task, never()).addCandidateGroups(anyCollection()); verify(fixture.execution, never()).getVariable(anyString());
        }
    }

    @Test void foreign_native_source_and_raw_assignment_claims_fail_before_loading_a_release() throws Exception {
        try (var fixture = new Fixture(false, Map.of())) {
            when(fixture.task.getTenantId()).thenReturn("21"); assertThrows(IllegalStateException.class, fixture::assign); when(fixture.task.getTenantId()).thenReturn("20");
            when(fixture.task.getExecutionId()).thenReturn("other"); assertThrows(IllegalStateException.class, fixture::assign); when(fixture.task.getExecutionId()).thenReturn("execution");
            assertThrows(IllegalStateException.class, () -> fixture.behavior.handleAssignments(fixture.taskService, "raw-user", null, List.of(), List.of(), fixture.task, fixture.expressions, fixture.execution, fixture.process));
            assertThrows(IllegalStateException.class, () -> fixture.behavior.handleAssignments(fixture.taskService, null, null, List.of("raw-user"), List.of(), fixture.task, fixture.expressions, fixture.execution, fixture.process));
            assertThrows(IllegalStateException.class, () -> fixture.behavior.handleAssignments(fixture.taskService, null, null, List.of(), List.of("role:50"), fixture.task, fixture.expressions, fixture.execution, fixture.process));
            assertTrue(fixture.releases.constructed().isEmpty()); verify(fixture.task, never()).addCandidateGroups(anyCollection());
        }
    }

    @Test void an_unknown_release_definition_cannot_fall_through_to_plain_native_assignment() throws Exception {
        try (var fixture = new Fixture(false, Map.of())) {
            when(fixture.execution.getProcessDefinitionId()).thenReturn("foreign-definition"); when(fixture.task.getProcessDefinitionId()).thenReturn("foreign-definition");
            assertThrows(IllegalStateException.class, fixture::assign); verify(fixture.task, never()).addCandidateGroups(anyCollection());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final SpringProcessEngineConfiguration process = mock(SpringProcessEngineConfiguration.class); final SpringDmnEngineConfiguration dmn = mock(SpringDmnEngineConfiguration.class);
        final TaskEntity task = mock(TaskEntity.class); final TaskService taskService = mock(TaskService.class); final DelegateExecution execution = mock(DelegateExecution.class); final ExpressionManager expressions = mock(ExpressionManager.class);
        final UserTask userTask; final PinnedHumanTaskActivityBehavior behavior;
        final org.mockito.MockedStatic<CommandContextUtil> context;
        final org.mockito.MockedConstruction<ProcessReleaseService> releases; final org.mockito.MockedConstruction<VerifiedProcessModels> models;
        Fixture(boolean dynamic, Map<String, ValueType> inputs) throws Exception {
            String xml = resource("governed-human-task.bpmn20.xml");
            if (dynamic) xml = xml.replace("task:initialLane=\"finance\"", "task:laneVariable=\"queue\"")
                    .replace("<startEvent id=\"start\"/>", "<startEvent id=\"start\"/><serviceTask id=\"decision\" xmlns:flowable=\"http://flowable.org/bpmn\" flowable:type=\"dmn\"><extensionElements><flowable:field name=\"decisionTableReferenceKey\"><flowable:string>routing</flowable:string></flowable:field></extensionElements></serviceTask>")
                    .replace("sourceRef=\"start\" targetRef=\"review\"", "sourceRef=\"start\" targetRef=\"decision\"")
                    .replace("<sequenceFlow id=\"reviewRoute\"", "<sequenceFlow id=\"decisionReview\" sourceRef=\"decision\" targetRef=\"review\"/><sequenceFlow id=\"reviewRoute\"");
            var validator = new ProcessModelValidator(); byte[] bytes = xml.getBytes(StandardCharsets.UTF_8); var model = validator.validateBpmn(bytes, "humanReview");
            var decision = validator.validateDmn(resource("baseline.dmn").replace("baselineDecision", "routing").replace("name=\"route\"", "name=\"queue\"").replace("'standard'", "'finance'").replace("'review'", "'manager'").getBytes(StandardCharsets.UTF_8), "routing");
            var definition = new Definition(Type.BPMN, "humanReview", "definition", "deployment", 1, java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), inputs);
            var decisionBinding = new Definition(Type.DMN, "routing", "decision-definition", "decision-deployment", 1, "a".repeat(64), decision.inputs());
            userTask = assertInstanceOf(UserTask.class, new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(bytes), true, true).getMainProcess().getFlowElement("review")); behavior = new PinnedHumanTaskActivityBehavior(userTask);
            when(execution.getTenantId()).thenReturn("20"); when(execution.getCurrentActivityId()).thenReturn("review"); when(execution.getId()).thenReturn("execution"); when(execution.getProcessInstanceId()).thenReturn("instance"); when(execution.getProcessDefinitionId()).thenReturn("definition");
            when(task.getTenantId()).thenReturn("20"); when(task.getTaskDefinitionKey()).thenReturn("review"); when(task.getExecutionId()).thenReturn("execution"); when(task.getProcessInstanceId()).thenReturn("instance"); when(task.getProcessDefinitionId()).thenReturn("definition");
            context = mockStatic(CommandContextUtil.class); context.when(CommandContextUtil::getProcessEngineConfiguration).thenReturn(process); context.when(CommandContextUtil::getDmnEngineConfiguration).thenReturn(dmn);
            releases = mockConstruction(ProcessReleaseService.class, (value, ignored) -> when(value.getForProcessDefinition(eq(20L), anyString())).thenReturn(new Release("release", "a".repeat(64), Instant.EPOCH, dynamic ? List.of(definition, decisionBinding) : List.of(definition))));
            models = mockConstruction(VerifiedProcessModels.class, (value, ignored) -> { when(value.runningProcess(20, definition)).thenReturn(model); when(value.decision(20, decisionBinding)).thenReturn(decision); });
        }
        void assign() { behavior.handleAssignments(taskService, null, null, List.of(), List.of(), task, expressions, execution, process); }
        @Override public void close() { models.close(); releases.close(); context.close(); }
    }
    private static String resource(String name) throws Exception { try (var input = PinnedHumanTaskActivityBehaviorTest.class.getResourceAsStream("/fixtures/" + name)) { assertNotNull(input); return new String(input.readAllBytes(), StandardCharsets.UTF_8); } }
}
