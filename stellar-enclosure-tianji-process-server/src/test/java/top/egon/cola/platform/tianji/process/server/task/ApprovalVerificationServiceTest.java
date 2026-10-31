package top.egon.cola.platform.tianji.process.server.task;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.ApprovalVerificationContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskActionResult.Entry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Policy/transaction markers over durable reference values; no IAM, native task engine or PostgreSQL runs here. */
class ApprovalVerificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final ProcessActor CONSUMER = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent-tools", "https://idp.example/20");
    private static final ProcessActor REQUESTER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "applicant", "https://idp.example/20");
    private static final ProcessActor REVIEWER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://idp.example/20");
    private static final StartProcessCommand START = new StartProcessCommand("approval", "release", "invoice-1", "start", "correlation", Map.of("inputSha256", HASH, "objectVersion", "7"));

    @Test void authentic_true_and_false_decisions_have_fixed_source_and_short_live_windows_without_consuming_or_mutating_a_reference() throws Exception {
        try (var fixture = new Fixture()) {
            var query = query(fixture.binding, START); var context = fixture.context();
            when(fixture.store.approval(context, "approval-ref")).thenReturn(Optional.of(entry(true, "50", NOW.minusSeconds(1), START)));
            var approved = fixture.service.verify(context, query); assertTrue(approved.approved()); assertEquals(query.fingerprint(), approved.requestSha256()); assertEquals(NOW.plusSeconds(20), approved.expiresAt());
            assertEquals(OrchestrationRequestDigests.start(START), approved.sourceRequestSha256()); assertEquals("start", approved.sourceStartCommandId());
            when(fixture.store.approval(context, "approval-ref")).thenReturn(Optional.of(entry(false, "50", NOW.minusSeconds(1), START)));
            assertFalse(fixture.service.verify(context, query).approved());
            verify(fixture.store, never()).apply(any(), any()); verify(fixture.store, never()).reserve(any(), any(), anyString(), anyString(), any()); verify(fixture.connection, times(2)).commit();
        }
    }

    @Test void requester_command_body_object_version_role_model_and_age_mismatches_cannot_verify() throws Exception {
        try (var fixture = new Fixture()) {
            var query = query(fixture.binding, START); var context = fixture.context();
            var changedVersion = new StartProcessCommand(START.definitionKey(), START.releaseVersion(), START.businessKey(), START.commandId(), START.correlationId(), Map.of("inputSha256", HASH, "objectVersion", "8"));
            for (var stored : List.of(entry(true, "60", NOW.minusSeconds(1), START), entry(true, "50", NOW.minusSeconds(61), START), entry(true, "50", NOW.plusSeconds(1), START), entry(true, "50", NOW.minusSeconds(1), changedVersion))) {
                when(fixture.store.approval(context, "approval-ref")).thenReturn(Optional.of(stored)); assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.verify(context, query));
            }
            when(fixture.store.approval(context, "approval-ref")).thenReturn(Optional.of(entry(true, "50", NOW.minusSeconds(1), START)));
            var differentRequester = new VerifyApprovalQuery(query.policyRef(), query.policyBindingSha256(), query.approvalReceiptId(), ApprovalRequesterTypeEnum.USER, "someone-else", START);
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.verify(context, differentRequester));
            var differentCommand = new StartProcessCommand(START.definitionKey(), START.releaseVersion(), START.businessKey(), "different", START.correlationId(), START.variables());
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.verify(context, query(fixture.binding, differentCommand)));
            var changedModel = new ApprovalConsumerRegistry.Registration(CONSUMER, "approval:v1", "release", "approval", "b".repeat(64), "review", Set.of("50"), Duration.ofSeconds(60), Set.of(ApprovalRequesterTypeEnum.USER));
            var changedService = new ApprovalVerificationService(fixture.source, fixture.manager, fixture.store, new ApprovalConsumerRegistry(List.of(changedModel)));
            assertThrows(ProcessResourceNotFoundException.class, () -> changedService.verify(context, query(changedModel, START)));
        }
    }

    @Test void unknown_consumers_changed_policy_and_service_requester_aliases_are_rejected_before_storage_io() throws Exception {
        try (var fixture = new Fixture()) {
            var query = query(fixture.binding, START);
            var foreign = new ApprovalVerificationContext(ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, CONSUMER.subjectId(), "https://other.example/20"), NOW.plusSeconds(20));
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.verify(foreign, query));
            var changed = new VerifyApprovalQuery(query.policyRef(), "b".repeat(64), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), START);
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.verify(fixture.context(), changed));
            verify(fixture.store, never()).approval(any(), anyString());
            var serviceBinding = new ApprovalConsumerRegistry.Registration(CONSUMER, "service-approval", "release", "approval", HASH, "review", Set.of("50"), Duration.ofSeconds(60), Set.of(ApprovalRequesterTypeEnum.SERVICE));
            var registry = new ApprovalConsumerRegistry(List.of(serviceBinding));
            assertEquals(serviceBinding, registry.require(CONSUMER, new VerifyApprovalQuery(serviceBinding.policyRef(), serviceBinding.fingerprint(), "approval-ref", ApprovalRequesterTypeEnum.SERVICE, CONSUMER.subjectId(), START)));
            assertThrows(ProcessResourceNotFoundException.class, () -> registry.require(CONSUMER, new VerifyApprovalQuery(serviceBinding.policyRef(), serviceBinding.fingerprint(), "approval-ref", ApprovalRequesterTypeEnum.SERVICE, "other-service", START)));
        }
    }

    @Test void ambient_transactions_missing_proofs_and_current_clock_failure_do_not_return_a_projection() throws Exception {
        try (var fixture = new Fixture()) {
            var query = query(fixture.binding, START);
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(fixture.manager).execute(status -> fixture.service.verify(fixture.context(), query))); verify(fixture.store, never()).approval(any(), anyString());
            assertThrows(ProcessResourceNotFoundException.class, () -> fixture.service.verify(fixture.context(), query));
            when(fixture.store.approval(any(), anyString())).thenReturn(Optional.of(entry(true, "50", NOW.minusSeconds(1), START))); when(fixture.store.approvalNow(any())).thenThrow(new IllegalStateException("clock unavailable"));
            assertThrows(IllegalStateException.class, () -> fixture.service.verify(fixture.context(), query));
        }
    }

    @Test void bindings_are_immutable_and_their_fingerprint_covers_consumer_and_current_policy_constraints() throws Exception {
        try (var fixture = new Fixture()) {
            assertThrows(IllegalArgumentException.class, () -> new ApprovalConsumerRegistry(List.of(fixture.binding, fixture.binding)));
            assertThrows(UnsupportedOperationException.class, () -> fixture.binding.eligibleRoles().clear());
            var changed = new ApprovalConsumerRegistry.Registration(CONSUMER, fixture.binding.policyRef(), "release", "approval", HASH, "review", Set.of("50"), Duration.ofSeconds(59), Set.of(ApprovalRequesterTypeEnum.USER));
            assertNotEquals(fixture.binding.fingerprint(), changed.fingerprint());
            assertThrows(IllegalArgumentException.class, () -> new ApprovalVerificationContext(REQUESTER, NOW.plusSeconds(20)));
            assertThrows(IllegalArgumentException.class, () -> new ApprovalVerificationService(mock(DataSource.class), fixture.manager, fixture.store, new ApprovalConsumerRegistry(List.of(fixture.binding))));
        }
    }

    private static VerifyApprovalQuery query(ApprovalConsumerRegistry.Registration binding, StartProcessCommand start) { return new VerifyApprovalQuery(binding.policyRef(), binding.fingerprint(), "approval-ref", ApprovalRequesterTypeEnum.USER, REQUESTER.subjectId(), start); }
    private static Entry entry(boolean approved, String role, Instant occurred, StartProcessCommand start) {
        var receipt = new HumanTaskActionReceiptDTO("complete", "task", HumanTaskActionEnum.COMPLETE, HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "approval", "review", HumanTaskKindEnum.APPROVAL, "finance", HASH, null, "approval-ref", approved, occurred);
        var origin = new TaskOrigin(REQUESTER, start.commandId(), OrchestrationRequestDigests.start(start), "release", "correlation", "instance", NOW.minusSeconds(120));
        return new Entry(REVIEWER, HASH, receipt, origin, HASH, "definition", role, 2, 3, "user-" + HASH, null);
    }
    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class); final Connection connection = mock(Connection.class); final HumanTaskReceiptStore store = mock(HumanTaskReceiptStore.class);
        final DataSourceTransactionManager manager; final ApprovalConsumerRegistry.Registration binding; final ApprovalVerificationService service;
        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true); when(store.usesDataSource(source)).thenReturn(true); when(store.approvalNow(any())).thenReturn(NOW);
            manager = new DataSourceTransactionManager(source); binding = new ApprovalConsumerRegistry.Registration(CONSUMER, "approval:v1", "release", "approval", HASH, "review", Set.of("50"), Duration.ofSeconds(60), Set.of(ApprovalRequesterTypeEnum.USER));
            service = new ApprovalVerificationService(source, manager, store, new ApprovalConsumerRegistry(List.of(binding)));
        }
        ApprovalVerificationContext context() { return new ApprovalVerificationContext(CONSUMER, NOW.plusSeconds(20)); }
        @Override public void close() { }
    }
}
