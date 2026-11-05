package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.job.api.Job;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReasonEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReportedFailureEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.LockedRoot;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.RecoveryDeadletter;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.RecoveryExecution;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.RecoveryBinding;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.RecoveryProvenance;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore.RecoveryLeaseEvidence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.workerOccurrenceKey;

final class WorkerRecoverySpecification {
    static final String READ_ONLY_EXHAUSTED_RETRY_V1 = "READ_ONLY_EXHAUSTED_RETRY_V1";
    private static final int MAX_HISTORY = 32;

    Decision evaluate(Facts facts) {
        String nativeActivity = nativeActivity(facts);
        var history = inspectHistory(facts, nativeActivity);
        String activity = nativeActivity != null ? nativeActivity : history.activityId();
        var reported = history.reportedFailure();
        var job = facts.deadletter();
        if (job == null) {
            var reason = reported == ReportedFailureEnum.UNVERIFIED || reported == ReportedFailureEnum.NONE
                    ? ReasonEnum.HISTORY_INCOMPLETE : failureReason(reported, ReasonEnum.NO_NATIVE_DEADLETTER);
            return new Decision(StatusEnum.HISTORICAL_FAILURE, reason, reported, activity, null, null, null);
        }
        if (facts.alreadyAccepted()) return manual(ReasonEnum.RESTORE_LIMIT_REACHED, reported, activity);
        if (unsupportedNative(job)) return manual(ReasonEnum.UNSUPPORTED_NATIVE_JOB, reported, activity);
        var provenance = facts.ordinaryReleasedProvenance().get();
        if (provenance.provesUnsupported()) return manual(ReasonEnum.UNSUPPORTED_NATIVE_JOB, reported, activity);
        if (facts.root() == null || facts.root().suspensionState() != 1) {
            return manual(ReasonEnum.PROCESS_INACTIVE, reported, activity);
        }

        var selected = history.qualifyingCandidate();
        if (nativeActivity == null || !matchingNativeScope(facts) || facts.collision() || history.conflicting()
                || history.rows().stream().anyMatch(row -> "COMPLETED".equals(row.state()))
                || selected != null && !facts.failureCodeMatches().test(selected)) {
            return manual(ReasonEnum.EVIDENCE_CONFLICT, reported, activity);
        }
        if (history.rows().stream().anyMatch(row -> "LEASED".equals(row.state()))) {
            return manual(ReasonEnum.ACTIVE_LEASE, reported, activity);
        }
        if (!provenance.ordinary()) return manual(ReasonEnum.BINDING_UNVERIFIED, reported, activity);
        var binding = selected == null ? null : facts.currentBinding().apply(provenance, selected);
        if (binding != null && (!binding.verified() || binding.effect() == null
                || !facts.root().businessKey().equals(binding.businessKey()))) {
            return manual(ReasonEnum.BINDING_UNVERIFIED, reported, activity);
        }
        if (!history.hasFinalFailure()) return manual(ReasonEnum.HISTORY_INCOMPLETE, reported, activity);
        var failureReason = failureReason(reported, null);
        if (failureReason != null) return manual(failureReason, reported, activity);
        if (selected == null || !history.hasOnlyEarlierRetryOrExpiry()) {
            return manual(ReasonEnum.EVIDENCE_CONFLICT, reported, activity);
        }
        if (binding == null) return manual(ReasonEnum.BINDING_UNVERIFIED, reported, activity);
        if (binding.effect() != WorkerEffect.READ_ONLY) return manual(ReasonEnum.UNSUPPORTED_EFFECT, reported, activity);

        String evidenceSha256 = evidenceDigest(history.rows());
        return new Decision(StatusEnum.RETRY_AVAILABLE, ReasonEnum.NONE, reported, activity,
                version(facts, evidenceSha256), selected, evidenceSha256);
    }

    private static boolean unsupportedNative(RecoveryDeadletter job) {
        return !Job.JOB_TYPE_EXTERNAL_WORKER.equals(job.type()) || !"external-worker-complete".equals(job.handler())
                || job.scopeType() != null && !"bpmn".equals(job.scopeType());
    }

    private static String nativeActivity(Facts facts) {
        var job = facts.deadletter();
        var execution = facts.execution();
        var start = facts.start();
        var definition = facts.definition();
        if (job == null || execution == null || definition.type() != Type.BPMN || !compatibleBpmnScope(job)
                || !facts.jobId().equals(job.jobId())
                || !Long.toString(start.requester().tenantId()).equals(job.tenant())
                || !start.instanceId().equals(job.instanceId()) || !definition.engineDefinitionId().equals(job.definitionId())
                || !safeText(job.executionId(), 255) || !job.executionId().equals(execution.executionId())
                || !matches(job.elementId(), "[A-Za-z_][A-Za-z0-9_.-]{0,127}") || !job.elementId().equals(execution.activityId())
                || !job.tenant().equals(execution.tenant()) || !job.instanceId().equals(execution.instanceId())
                || !job.definitionId().equals(execution.definitionId())) return null;
        return job.elementId();
    }

    private static boolean matchingNativeScope(Facts facts) {
        var job = facts.deadletter();
        var start = facts.start();
        var root = facts.root();
        return matches(start.requester().issuerFingerprint(), "[0-9a-f]{64}")
                && root.tenantId() == start.requester().tenantId() && root.instanceId().equals(start.instanceId())
                && root.engineDefinitionId().equals(facts.definition().engineDefinitionId())
                && job.revision() != null && job.revision() > 0 && safeText(job.topic(), 255)
                && (job.correlationId() == null || safeText(job.correlationId(), 255));
    }

    private static boolean compatibleBpmnScope(RecoveryDeadletter job) {
        return (job.scopeType() == null || "bpmn".equals(job.scopeType()))
                && (job.scopeId() == null || job.scopeId().equals(job.instanceId()))
                && (job.subScopeId() == null || job.subScopeId().equals(job.executionId()))
                && (job.scopeDefinitionId() == null || job.scopeDefinitionId().equals(job.definitionId()));
    }

    private static History inspectHistory(Facts facts, String nativeActivity) {
        if (facts.history().size() > MAX_HISTORY) return new History(List.of(), ReportedFailureEnum.UNVERIFIED, null, false);
        if (facts.history().isEmpty()) return new History(List.of(), ReportedFailureEnum.NONE, null, false);

        var first = facts.history().getFirst();
        var job = facts.deadletter();
        String historyTopic;
        try {
            historyTopic = ProcessModelValidator.nativeWorkerTopic(first.topic(), first.schemaVersion());
        } catch (IllegalArgumentException invalidTopic) {
            return conflictingHistory();
        }
        if (nativeActivity != null && !unsupportedNative(job)
                && (!nativeActivity.equals(first.elementId()) || !historyTopic.equals(job.topic()))) {
            return conflictingHistory();
        }
        String occurrenceKey = workerOccurrenceKey(facts.start().requester().tenantId(), facts.jobId());
        for (var row : facts.history()) {
            if (!matchingHistoryScope(facts, row, first, occurrenceKey) || !validLeaseState(row)) {
                return conflictingHistory();
            }
        }

        var rows = facts.history().stream().sorted(Comparator.comparing(RecoveryLeaseEvidence::leasedAt)).toList();
        for (int i = 1; i < rows.size(); i++) {
            var prior = rows.get(i - 1);
            var leaseTime = rows.get(i).leasedAt();
            var priorEnd = prior.decidedAt() != null ? prior.decidedAt() : prior.leaseExpiresAt();
            if (!prior.leasedAt().isBefore(leaseTime) || !priorEnd.isBefore(leaseTime)) {
                return conflictingHistory();
            }
        }
        return new History(rows, reportedFailure(rows), first.elementId(), false);
    }

    private static History conflictingHistory() {
        return new History(List.of(), ReportedFailureEnum.UNVERIFIED, null, true);
    }

    private static boolean matchingHistoryScope(Facts facts, RecoveryLeaseEvidence row, RecoveryLeaseEvidence first,
                                                 String occurrenceKey) {
        var start = facts.start();
        var definition = facts.definition();
        return definition.type() == Type.BPMN && row.tenantId() == start.requester().tenantId()
                && "BPMN".equals(row.scopeType()) && facts.jobId().equals(row.jobId())
                && facts.jobId().equals(row.occurrenceId()) && start.instanceId().equals(row.scopeId())
                && start.releaseKey().equals(row.releaseKey()) && definition.engineDefinitionId().equals(row.definitionId())
                && definition.modelSha256().equals(row.modelSha256()) && matches(row.modelSha256(), "[0-9a-f]{64}")
                && occurrenceKey.equals(row.idempotencyKey()) && matches(row.elementId(), "[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                && row.elementId().equals(first.elementId())
                && first.topic().equals(row.topic()) && first.schemaVersion().equals(row.schemaVersion())
                && matches(row.subjectIssuer(), "[0-9a-f]{64}") && safeText(row.subjectId(), 256)
                && matches(row.inputSha256(), "[0-9a-f]{64}");
    }

    private static boolean validLeaseState(RecoveryLeaseEvidence row) {
        var leased = row.leasedAt();
        var expiry = row.leaseExpiresAt();
        var decided = row.decidedAt();
        if (leased == null || expiry == null || !leased.isBefore(expiry) || row.state() == null) return false;
        boolean noFailure = row.failureKind() == null && row.failureCode() == null;
        boolean noResult = row.resultSha256() == null && !row.hasBusinessReceipt() && noFailure;
        if ("LEASED".equals(row.state())) return decided == null && noResult;
        if ("EXPIRED".equals(row.state())) return decided != null && !decided.isBefore(expiry) && noResult;
        if (decided == null || !leased.isBefore(decided) || !decided.isBefore(expiry)
                || !matches(row.resultSha256(), "[0-9a-f]{64}")) return false;
        if ("COMPLETED".equals(row.state())) return row.hasBusinessReceipt() && noFailure;
        if (row.hasBusinessReceipt() || row.failureKind() == null || !matches(row.failureCode(), "[A-Z][A-Z0-9_]{0,63}")) {
            return false;
        }
        return switch (row.failureKind()) {
            case "RETRYABLE" -> ("NEEDS_MANUAL".equals(row.state()) || "RETRY_SCHEDULED".equals(row.state()))
                    && ProcessRequestDigest.hash(List.of("worker-fail-v1", row.jobId(), row.occurrenceId(),
                    row.failureKind(), row.failureCode()), Map.of()).equals(row.resultSha256());
            case "BUSINESS_REJECTED" -> "BUSINESS_REJECTED".equals(row.state()) || "NEEDS_MANUAL".equals(row.state());
            case "UNKNOWN" -> "NEEDS_MANUAL".equals(row.state());
            default -> false;
        };
    }

    private static ReportedFailureEnum reportedFailure(List<RecoveryLeaseEvidence> rows) {
        if (rows.stream().anyMatch(row -> "UNKNOWN".equals(row.failureKind()))) return ReportedFailureEnum.UNKNOWN;
        if (rows.stream().anyMatch(row -> "BUSINESS_REJECTED".equals(row.failureKind()))) return ReportedFailureEnum.BUSINESS_REJECTED;
        return rows.stream().anyMatch(row -> "RETRYABLE".equals(row.failureKind()))
                ? ReportedFailureEnum.RETRYABLE : ReportedFailureEnum.NONE;
    }

    private static ReasonEnum failureReason(ReportedFailureEnum reported, ReasonEnum otherwise) {
        return switch (reported) {
            case UNKNOWN -> ReasonEnum.UNKNOWN_OUTCOME;
            case BUSINESS_REJECTED -> ReasonEnum.BUSINESS_REJECTED;
            default -> otherwise;
        };
    }

    private static Decision manual(ReasonEnum reason, ReportedFailureEnum reported, String activity) {
        return new Decision(StatusEnum.MANUAL_REVIEW, reason, reported, activity, null, null, null);
    }

    private static String evidenceDigest(List<RecoveryLeaseEvidence> rows) {
        var fields = new ArrayList<>(List.of("TIANJI_WORKER_RECOVERY_EVIDENCE_V1", READ_ONLY_EXHAUSTED_RETRY_V1,
                Integer.toString(rows.size())));
        for (var row : rows) {
            fields.addAll(List.of(Long.toString(row.tenantId()), row.jobId(), row.occurrenceId(), row.scopeType(),
                    row.scopeId(), row.releaseKey(), row.definitionId(), row.elementId(), row.topic(), row.schemaVersion(),
                    row.idempotencyKey(), row.modelSha256(), row.inputSha256()));
            addNullable(fields, row.resultSha256());
            fields.add(row.state());
            addNullable(fields, row.failureKind());
            addNullable(fields, row.failureCode());
            fields.addAll(List.of(row.subjectIssuer(), row.subjectId(), row.leasedAt().toString(), row.leaseExpiresAt().toString()));
            addNullable(fields, Objects.toString(row.decidedAt(), null));
        }
        return ProcessRequestDigest.hash(fields, Map.of());
    }

    private static String version(Facts facts, String evidenceSha256) {
        var start = facts.start();
        var owner = start.requester();
        var definition = facts.definition();
        var root = facts.root();
        var execution = facts.execution();
        var job = facts.deadletter();
        var fields = new ArrayList<>(List.of("TIANJI_WORKER_RECOVERY_VERSION_V1", Long.toString(owner.tenantId()),
                owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), start.commandId(),
                start.requestHash(), start.releaseKey(), start.instanceId(), start.correlationId(),
                definition.definitionKey(), definition.engineDefinitionId(), definition.modelSha256(), root.businessKey(),
                root.instanceId(), Integer.toString(root.revision()), Integer.toString(root.suspensionState()),
                execution.executionId(), execution.activityId(), execution.tenant(), execution.instanceId(),
                execution.definitionId(), job.jobId(), Integer.toString(job.revision()), job.type(), job.tenant(),
                job.instanceId(), job.definitionId(), job.executionId(), job.elementId()));
        addNullable(fields, job.scopeId());
        addNullable(fields, job.subScopeId());
        addNullable(fields, job.scopeType());
        addNullable(fields, job.scopeDefinitionId());
        fields.addAll(List.of(job.handler(), job.topic()));
        addNullable(fields, job.correlationId());
        fields.add(evidenceSha256);
        return ProcessRequestDigest.hash(fields, Map.of());
    }

    private static void addNullable(List<String> fields, String value) {
        fields.add(value == null ? "0" : "1");
        if (value != null) fields.add(value);
    }

    private static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    private static boolean safeText(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.equals(value.trim())
                && value.codePoints().noneMatch(c -> c < 32 || c == 127 || c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE);
    }

    record Facts(TaskOrigin start, Definition definition, String jobId, LockedRoot root, RecoveryDeadletter deadletter,
                 RecoveryExecution execution, boolean collision, boolean alreadyAccepted, List<RecoveryLeaseEvidence> history,
                 Supplier<RecoveryProvenance> ordinaryReleasedProvenance, Predicate<RecoveryLeaseEvidence> failureCodeMatches,
                 BiFunction<RecoveryProvenance, RecoveryLeaseEvidence, RecoveryBinding> currentBinding) {
        Facts { history = List.copyOf(history); }
    }

    record Decision(StatusEnum status, ReasonEnum reason, ReportedFailureEnum reportedFailure, String activityId,
                    String version, RecoveryLeaseEvidence qualifying, String evidenceSha256) { }

    private record History(List<RecoveryLeaseEvidence> rows, ReportedFailureEnum reportedFailure,
                           String activityId, boolean conflicting) {
        private boolean hasFinalFailure() {
            return !rows.isEmpty() && ("NEEDS_MANUAL".equals(rows.getLast().state())
                    || "BUSINESS_REJECTED".equals(rows.getLast().state()));
        }

        private RecoveryLeaseEvidence qualifyingCandidate() {
            if (!hasFinalFailure() || !isExhaustedRetryable(rows.getLast())) return null;
            return rows.stream().filter(History::isExhaustedRetryable).count() == 1 ? rows.getLast() : null;
        }

        private static boolean isExhaustedRetryable(RecoveryLeaseEvidence row) {
            return "NEEDS_MANUAL".equals(row.state()) && "RETRYABLE".equals(row.failureKind());
        }

        private boolean hasOnlyEarlierRetryOrExpiry() {
            return rows.subList(0, rows.size() - 1).stream()
                    .allMatch(row -> "EXPIRED".equals(row.state()) || "RETRY_SCHEDULED".equals(row.state()));
        }
    }
}
