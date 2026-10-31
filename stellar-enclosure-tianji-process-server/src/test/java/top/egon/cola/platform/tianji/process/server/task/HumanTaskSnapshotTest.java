package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Controlled native facts prove mapping/version boundaries; no Flowable engine or JDBC transaction is started. */
class HumanTaskSnapshotTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String ISSUER = "https://idp.example/20";
    private static final ProcessActor REQUESTER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "applicant", ISSUER);
    private static final ProcessActor REVIEWER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", ISSUER);
    private final TaskEntity task = mock(TaskEntity.class);
    private final TaskOrigin origin = new TaskOrigin(REQUESTER, "start-1", "a".repeat(64), "release", "correlation", "instance", NOW);
    private final HumanTaskContext context = new HumanTaskContext(REVIEWER, "tianji.api.process.task.get", Set.of("50"), NOW.plusSeconds(30), 2, 3);
    private ValidatedProcessModel model;
    private Definition definition;
    private List<IdentityLink> links;

    @BeforeEach void setup() throws Exception {
        byte[] bytes; try (var input = getClass().getResourceAsStream("/fixtures/governed-human-task.bpmn20.xml")) { assertNotNull(input); bytes = input.readAllBytes(); }
        model = new ProcessModelValidator().validateBpmn(bytes, "humanReview");
        definition = new Definition(Type.BPMN, "humanReview", "definition-id", "deployment-id", 1, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), Map.of());
        when(task.getId()).thenReturn("task"); when(task.getTaskDefinitionKey()).thenReturn("review"); when(task.getName()).thenReturn("Review"); when(task.getProcessInstanceId()).thenReturn("instance");
        when(task.getProcessDefinitionId()).thenReturn("definition-id"); when(task.getExecutionId()).thenReturn("execution"); when(task.getTenantId()).thenReturn("20"); when(task.getCreateTime()).thenReturn(Date.from(NOW));
        when(task.getState()).thenReturn("CREATED"); when(task.getRevision()).thenReturn(1); links = List.of(group("50"), group("51"));
    }

    @Test void projection_uses_only_released_form_names_and_current_membership_without_process_variable_or_role_payload() {
        var snapshot = snapshot(); var value = snapshot.projection(context);
        assertEquals("finance", value.lane()); assertEquals("approved", value.approvalField()); assertEquals(Map.of("approved", ProcessValueTypeEnum.BOOLEAN, "comment", ProcessValueTypeEnum.STRING), value.formSchema());
        assertEquals(snapshot.version(), value.version()); assertFalse(value.assigned()); assertFalse(value.assignedToMe()); assertEquals("instance", value.scopeId());
        verify(task, never()).getProcessVariables(); verify(task, never()).getTaskLocalVariables(); assertFalse(snapshot.toString().contains("applicant"));
        var foreign = new HumanTaskContext(ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://other-idp.example/20"), context.resourceKey(), Set.of("50"), context.deadlineAt(), 2, 3);
        assertThrows(ProcessResourceNotFoundException.class, () -> snapshot.projection(foreign));
    }

    @Test void native_revision_prevents_aba_even_when_public_assignment_state_returns_to_the_same_values() {
        var first = snapshot(); links = List.of(links.get(1), links.getFirst()); assertEquals(first.version(), snapshot().version());
        when(task.getRevision()).thenReturn(2); var changed = snapshot(); assertNotEquals(first.version(), changed.version());
        when(task.getAssignee()).thenReturn(context.userKey()); when(task.getState()).thenReturn("CLAIMED"); when(task.getClaimTime()).thenReturn(Date.from(NOW.plusSeconds(1))); assertNotEquals(changed.version(), snapshot().version());
        when(task.getAssignee()).thenReturn(null); when(task.getState()).thenReturn("CREATED"); when(task.getClaimTime()).thenReturn(null); when(task.getRevision()).thenReturn(3);
        assertNotEquals(first.version(), snapshot().version());
    }

    @Test void native_candidates_must_identify_one_complete_pinned_lane_and_never_accept_candidate_users_or_extra_roles() {
        var first = snapshot(); links = List.of(group("60")); var manager = snapshot(); assertEquals("manager", manager.lane()); assertNotEquals(first.version(), manager.version());
        links = List.of(group("50")); assertThrows(IllegalStateException.class, this::snapshot);
        links = List.of(group("50"), group("51"), group("99")); assertThrows(IllegalStateException.class, this::snapshot);
        var user = mock(IdentityLink.class); when(user.getTaskId()).thenReturn("task"); when(user.getType()).thenReturn("candidate"); when(user.getUserId()).thenReturn("raw-user");
        links = List.of(group("50"), group("51"), user); assertThrows(IllegalStateException.class, this::snapshot);
    }

    @Test void wrong_source_tenant_definition_model_bytes_or_unmanaged_task_cannot_be_projected_as_governed_work() {
        when(task.getTenantId()).thenReturn("21"); assertThrows(IllegalStateException.class, this::snapshot); when(task.getTenantId()).thenReturn("20");
        when(task.getProcessInstanceId()).thenReturn("other"); assertThrows(IllegalStateException.class, this::snapshot); when(task.getProcessInstanceId()).thenReturn("instance");
        when(task.getTaskDefinitionKey()).thenReturn("unmanaged"); assertThrows(IllegalStateException.class, this::snapshot); when(task.getTaskDefinitionKey()).thenReturn("review");
        definition = new Definition(Type.BPMN, "humanReview", "definition-id", "deployment-id", 1, "b".repeat(64), Map.of()); assertThrows(IllegalStateException.class, this::snapshot);
        assertThrows(IllegalStateException.class, () -> new HumanTaskSnapshot(mock(Task.class), links, origin, definition, model));
    }

    private HumanTaskSnapshot snapshot() { return new HumanTaskSnapshot(task, links, origin, definition, model); }
    private static IdentityLink group(String id) { var link = mock(IdentityLink.class); when(link.getTaskId()).thenReturn("task"); when(link.getType()).thenReturn("candidate"); when(link.getGroupId()).thenReturn("role:" + id); return link; }
}
