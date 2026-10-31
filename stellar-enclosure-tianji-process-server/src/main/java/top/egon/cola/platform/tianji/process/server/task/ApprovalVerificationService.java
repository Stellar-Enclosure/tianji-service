package top.egon.cola.platform.tianji.process.server.task;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ApprovalVerificationContext;

import javax.sql.DataSource;
import java.util.Objects;

/** Read-only provenance verification. The business dispatcher must separately authorize, bind and consume a true decision. */
public final class ApprovalVerificationService {
    private final HumanTaskReceiptStore store;
    private final ApprovalConsumerRegistry consumers;
    private final TransactionTemplate read;

    public ApprovalVerificationService(DataSource source, DataSourceTransactionManager manager, HumanTaskReceiptStore store, ApprovalConsumerRegistry consumers) {
        this.store = Objects.requireNonNull(store, "store"); this.consumers = Objects.requireNonNull(consumers, "consumers");
        if (source == null || manager == null || manager.getDataSource() != source || !store.usesDataSource(source)) throw new IllegalArgumentException("Approval verification must use the owning receipt transaction manager");
        read = new TransactionTemplate(manager); read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); read.setTimeout(10);
    }
    public VerifiedApprovalDTO verify(ApprovalVerificationContext context, VerifyApprovalQuery query) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(query, "query");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Approval verification must own its short transaction");
        var binding = consumers.require(context.actor(), query);
        return Objects.requireNonNull(read.execute(status -> {
            var entry = store.approval(context, query.approvalReceiptId()).orElseThrow(ProcessResourceNotFoundException::new); var result = entry.receipt(); var origin = entry.origin();
            if (entry.actor().tenantId() != context.actor().tenantId() || !entry.actor().issuerFingerprint().equals(context.actor().issuerFingerprint())
                    || result.action() != HumanTaskActionEnum.COMPLETE || result.kind() != HumanTaskKindEnum.APPROVAL || !query.approvalReceiptId().equals(result.approvalReceiptId())
                    || !binding.releaseVersion().equals(result.releaseVersion()) || !binding.definitionKey().equals(result.definitionKey()) || !binding.definitionSha256().equals(entry.modelSha256())
                    || !binding.taskDefinitionKey().equals(result.taskDefinitionKey()) || !binding.eligibleRoles().contains(entry.authorizedRoleId())
                    || !origin.requester().subjectType().name().equals(query.requesterType().name()) || !origin.requester().subjectId().equals(query.requesterSubjectId())
                    || !origin.commandId().equals(query.sourceStart().commandId()) || !origin.requestHash().equals(OrchestrationRequestDigests.start(query.sourceStart()))) throw new ProcessResourceNotFoundException();
            var now = store.approvalNow(context); var expires = result.occurredAt().plus(binding.maximumAge());
            if (result.occurredAt().isAfter(now) || !expires.isAfter(now)) throw new ProcessResourceNotFoundException();
            if (now.plusSeconds(30).isBefore(expires)) expires = now.plusSeconds(30);
            if (context.deadlineAt().isBefore(expires)) expires = context.deadlineAt();
            return new VerifiedApprovalDTO(binding.policyRef(), binding.fingerprint(), query.fingerprint(), result.approvalReceiptId(), query.requesterType(), origin.requester().subjectId(),
                    origin.commandId(), result.scopeId(), result.taskId(), result.releaseVersion(), result.definitionKey(), entry.modelSha256(), result.taskDefinitionKey(), origin.requestHash(),
                    result.approved(), result.occurredAt(), now, expires);
        }));
    }
}
