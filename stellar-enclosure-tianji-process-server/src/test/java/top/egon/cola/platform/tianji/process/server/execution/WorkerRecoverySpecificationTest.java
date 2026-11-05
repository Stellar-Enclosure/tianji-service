package top.egon.cola.platform.tianji.process.server.execution;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReasonEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReportedFailureEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.LockedRoot;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.RecoveryDeadletter;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess.RecoveryExecution;
import top.egon.cola.platform.tianji.process.server.execution.WorkerRecoverySpecification.Decision;
import top.egon.cola.platform.tianji.process.server.execution.WorkerRecoverySpecification.Facts;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.RecoveryBinding;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.RecoveryProvenance;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore.RecoveryLeaseEvidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService.workerOccurrenceKey;

class WorkerRecoverySpecificationTest {
    private static final Instant TIME = Instant.parse("2026-11-05T05:50:00.000001Z");
    private static final ProcessActor OWNER = new ProcessActor(42, ProcessActor.SubjectType.USER, "owner", hash('a'));
    private static final TaskOrigin START = new TaskOrigin(OWNER, "start-42", hash('b'), "release-42",
            "start-correlation", "proc-42", TIME);
    private static final Definition DEFINITION = new Definition(Type.BPMN, "ordinary", "engine-def", "deployment", 1,
            hash('c'), Map.of());
    private static final LockedRoot ROOT = new LockedRoot(42, "proc-42", "engine-def", "business-42", 1, 1);
    private static final RecoveryExecution EXECUTION = new RecoveryExecution("exec-18", "worker_1", "proc-42", "engine-def", "42");
    private static final RecoveryDeadletter NATIVE = nativeJob("externalWorker", "external-worker-complete", "exec-18",
            null, null, null, null, "scan:v1", null, 1);

    private final WorkerRecoverySpecification specification = new WorkerRecoverySpecification();

    @Test
    void dispositionPrecedenceAndReportedFailureAreIndependent() {
        var ordinary = ordinary();
        var drift = RecoveryBinding.unverified();
        var rejected = evidence(1, "BUSINESS_REJECTED", "BUSINESS_REJECTED", "REJECTED");
        var unknown = evidence(2, "NEEDS_MANUAL", "UNKNOWN", "UNKNOWN");
        var manual = evidence(3, "NEEDS_MANUAL", "RETRYABLE", "TIMEOUT");
        var history = List.of(rejected, unknown);
        assertDisposition(evaluate(null, null, history, false, false, ordinary, drift, true),
                StatusEnum.HISTORICAL_FAILURE, ReasonEnum.UNKNOWN_OUTCOME, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertDisposition(evaluate(null, null, List.of(rejected), false, false, ordinary, drift, true),
                StatusEnum.HISTORICAL_FAILURE, ReasonEnum.BUSINESS_REJECTED, ReportedFailureEnum.BUSINESS_REJECTED, "worker_1");
        assertDisposition(evaluate(null, null, List.of(manual), false, false, ordinary, drift, true),
                StatusEnum.HISTORICAL_FAILURE, ReasonEnum.NO_NATIVE_DEADLETTER, ReportedFailureEnum.RETRYABLE, "worker_1");
        assertDisposition(evaluate(null, null, List.of(), false, false, ordinary, drift, true),
                StatusEnum.HISTORICAL_FAILURE, ReasonEnum.HISTORY_INCOMPLETE, ReportedFailureEnum.NONE, null);
        var unsupported = nativeJob("timer", "timer-trigger", "exec-18", null, null, null, null, "scan:v1", null, 1);
        assertManual(evaluate(null, unsupported, history, true, true, RecoveryProvenance.unsupported(), drift, false),
                ReasonEnum.RESTORE_LIMIT_REACHED, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertManual(evaluate(null, unsupported, history, true, false, ordinary, drift, false),
                ReasonEnum.UNSUPPORTED_NATIVE_JOB, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertManual(evaluate(ROOT, nativeJob("externalWorker", "wrong-handler", "exec-18",
                        null, null, null, null, "scan:v1", null, 1), history, false, false, ordinary, drift, true),
                ReasonEnum.UNSUPPORTED_NATIVE_JOB, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertManual(evaluate(ROOT, nativeJob("externalWorker", "external-worker-complete", "exec-18",
                        "case-18", null, "cmmn", null, "scan:v1", null, 1), history, false, false, ordinary, drift, true),
                ReasonEnum.UNSUPPORTED_NATIVE_JOB, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertManual(evaluate(null, NATIVE, history, true, false, RecoveryProvenance.unsupported(), drift, false),
                ReasonEnum.UNSUPPORTED_NATIVE_JOB, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertManual(evaluate(null, NATIVE, history, true, false, ordinary, drift, false),
                ReasonEnum.PROCESS_INACTIVE, ReportedFailureEnum.UNKNOWN, "worker_1");
        var suspended = new LockedRoot(42, "proc-42", "engine-def", "business-42", 1, 2);
        assertManual(evaluate(suspended, NATIVE, history, true, false, ordinary, drift, false),
                ReasonEnum.PROCESS_INACTIVE, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, history, true, false, ordinary, drift, true),
                ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, history, false, false, ordinary, drift, true),
                ReasonEnum.UNKNOWN_OUTCOME, ReportedFailureEnum.UNKNOWN, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, List.of(rejected), false, false, ordinary, drift, true),
                ReasonEnum.BUSINESS_REJECTED, ReportedFailureEnum.BUSINESS_REJECTED, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, List.of(manual), false, false, ordinary, drift, true),
                ReasonEnum.BINDING_UNVERIFIED, ReportedFailureEnum.RETRYABLE, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, List.of(manual), false, false, ordinary,
                        RecoveryBinding.verified(WorkerEffect.TRANSACTIONAL_RECEIPT, ROOT.businessKey()), true),
                ReasonEnum.UNSUPPORTED_EFFECT, ReportedFailureEnum.RETRYABLE, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, List.of(manual), false, false, ordinary,
                        RecoveryBinding.verified(WorkerEffect.READ_ONLY, "wrong-business-key"), true),
                ReasonEnum.BINDING_UNVERIFIED, ReportedFailureEnum.RETRYABLE, "worker_1");
    }

    @Test
    void admitsExactlyOneExhaustedReadOnlyOccurrence() {
        var expired = evidence(0, "EXPIRED", null, null);
        var retried = evidence(1, "RETRY_SCHEDULED", "RETRYABLE", "RETRY");
        var last = evidence(2, "NEEDS_MANUAL", "RETRYABLE", "TIMEOUT");
        var decision = specification.evaluate(facts(List.of(expired, retried, last)));
        assertEquals(StatusEnum.RETRY_AVAILABLE, decision.status());
        assertEquals(ReasonEnum.NONE, decision.reason());
        assertEquals(ReportedFailureEnum.RETRYABLE, decision.reportedFailure());
        assertEquals("worker_1", decision.activityId());
        assertEquals(last, decision.qualifying());
        assertEquals("efc2c19f3d18d534742919dc650b270451cd37e848ccf73ef3dca1e71119fa31", decision.version());
        assertEquals("f8f159c2e315feb54c9f614c18c181261c686192311744f98708aa6a73024473", decision.evidenceSha256());
        assertEquals(decision, specification.evaluate(facts(List.of(last, retried, expired))));
        assertEquals("2026-11-05T05:52:10.000001Z", decision.qualifying().decidedAt().toString());
    }

    @Test
    void rejectsIncompleteContradictoryAndUnorderedHistory() {
        var ordinary = ordinary();
        var last = evidence(2, "NEEDS_MANUAL", "RETRYABLE", "TIMEOUT");
        var earlier = evidence(1, "RETRY_SCHEDULED", "RETRYABLE", "RETRY");
        var expired = evidence(0, "EXPIRED", null, null);
        assertManual(specification.evaluate(facts(List.of())), ReasonEnum.HISTORY_INCOMPLETE, ReportedFailureEnum.NONE, "worker_1");
        assertManual(specification.evaluate(facts(List.of(earlier, expired))), ReasonEnum.HISTORY_INCOMPLETE,
                ReportedFailureEnum.RETRYABLE, "worker_1");
        for (List<RecoveryLeaseEvidence> history : List.of(
                List.of(earlier, earlier, last),
                List.of(times(earlier, last.leasedAt(), earlier.leaseExpiresAt(), earlier.decidedAt()), last),
                List.of(times(earlier, earlier.leasedAt(), last.leaseExpiresAt(), last.leasedAt()), last),
                List.of(times(earlier, earlier.leasedAt(), last.leaseExpiresAt(), last.leasedAt().plusSeconds(1)), last),
                List.of(times(last, last.leasedAt(), last.leaseExpiresAt(), last.leaseExpiresAt())),
                List.of(times(last, last.leasedAt(), last.leaseExpiresAt(), last.leasedAt())),
                List.of(times(expired, expired.leasedAt(), expired.leaseExpiresAt(), expired.leaseExpiresAt().minusNanos(1)), last),
                List.of(withDefinitionAndTopic(last, "wrong-def", last.topic(), last.modelSha256())),
                List.of(withResult(last, hash('f'))),
                List.of(evidence(2, "NEEDS_MANUAL", "RETRYABLE", "unsafe code")),
                List.of(withResult(evidence(1, "EXPIRED", null, null), hash('f')), last))) {
            assertManual(specification.evaluate(facts(history)), ReasonEnum.EVIDENCE_CONFLICT,
                    ReportedFailureEnum.UNVERIFIED, "worker_1");
        }
        assertManual(specification.evaluate(facts(List.of(evidence(1, "COMPLETED", null, null), last))),
                ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.RETRYABLE, "worker_1");
        assertManual(specification.evaluate(facts(List.of(evidence(1, "NEEDS_MANUAL", "RETRYABLE", "RETRY"), last))),
                ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.RETRYABLE, "worker_1");
        var lease = evidence(0, "LEASED", null, null);
        assertManual(specification.evaluate(facts(List.of(lease, last))), ReasonEnum.ACTIVE_LEASE,
                ReportedFailureEnum.RETRYABLE, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, List.of(lease, last), false, false, ordinary, binding(), false),
                ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.RETRYABLE, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, List.of(last), false, false, ordinary, binding(), false),
                ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.RETRYABLE, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, List.of(), false, false, RecoveryProvenance.unverified(), binding(), true),
                ReasonEnum.BINDING_UNVERIFIED, ReportedFailureEnum.NONE, "worker_1");
        assertManual(evaluate(ROOT, NATIVE, List.of(lease), false, false, ordinary, binding(), true),
                ReasonEnum.ACTIVE_LEASE, ReportedFailureEnum.NONE, "worker_1");
        assertManual(specification.evaluate(facts(START, DEFINITION, ROOT, NATIVE, null, List.of(last))),
                ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.RETRYABLE, "worker_1");
        var bounded = IntStream.range(0, 32).mapToObj(i -> i == 31
                ? evidence(i, "NEEDS_MANUAL", "RETRYABLE", "TIMEOUT") : evidence(i, "EXPIRED", null, null)).toList();
        assertEquals(StatusEnum.RETRY_AVAILABLE, specification.evaluate(facts(bounded)).status());
        var overflow = IntStream.range(0, 33).mapToObj(i -> i == 32
                ? evidence(i, "NEEDS_MANUAL", "RETRYABLE", "TIMEOUT") : evidence(i, "EXPIRED", null, null)).toList();
        assertManual(specification.evaluate(facts(overflow)), ReasonEnum.HISTORY_INCOMPLETE,
                ReportedFailureEnum.UNVERIFIED, "worker_1");
    }

    @Test
    void versionBindsExactOwnerNativeAndEvidenceButNotClock() {
        var earlier = evidence(0, "RETRY_SCHEDULED", "RETRYABLE", "RETRY");
        var last = evidence(2, "NEEDS_MANUAL", "RETRYABLE", "TIMEOUT");
        var history = List.of(earlier, last);
        var baseline = specification.evaluate(facts(history));
        assertEquals(StatusEnum.RETRY_AVAILABLE, baseline.status());
        assertEquals(baseline, specification.evaluate(facts(START, DEFINITION, ROOT, NATIVE, EXECUTION, history)));
        assertEquals(baseline, specification.evaluate(facts(new TaskOrigin(OWNER, START.commandId(), START.requestHash(),
                START.releaseKey(), START.correlationId(), START.instanceId(), TIME.plusSeconds(1)), DEFINITION, ROOT,
                NATIVE, EXECUTION, history)));
        for (var owner : List.of(new ProcessActor(42, ProcessActor.SubjectType.SERVICE, OWNER.subjectId(), OWNER.issuerFingerprint()),
                new ProcessActor(42, OWNER.subjectType(), "different-owner", OWNER.issuerFingerprint()),
                new ProcessActor(42, OWNER.subjectType(), OWNER.subjectId(), hash('e')))) {
            assertVersionChange(baseline, facts(start(owner, START.requestHash()), DEFINITION, ROOT, NATIVE, EXECUTION, history));
        }
        assertVersionChange(baseline, facts(start(OWNER, hash('e')), DEFINITION, ROOT, NATIVE, EXECUTION, history));
        var otherModel = new Definition(Type.BPMN, "ordinary", "engine-def", "deployment", 1, hash('e'), Map.of());
        var otherModelHistory = history.stream().map(row ->
                withDefinitionAndTopic(row, row.definitionId(), row.topic(), otherModel.modelSha256())).toList();
        assertVersionChange(baseline, facts(START, otherModel, ROOT, NATIVE, EXECUTION, otherModelHistory));
        assertVersionChange(baseline, facts(START, DEFINITION,
                new LockedRoot(42, "proc-42", "engine-def", "business-42", 2, 1), NATIVE, EXECUTION, history));
        var otherNative = nativeJob("externalWorker", "external-worker-complete", "exec-18", null, null, null,
                null, "scan:v1", null, 2);
        assertVersionChange(baseline, facts(START, DEFINITION, ROOT, otherNative, EXECUTION, history));
        var otherExecution = new RecoveryExecution("exec-19", "worker_1", "proc-42", "engine-def", "42");
        otherNative = nativeJob("externalWorker", "external-worker-complete", "exec-19", null, null, null,
                null, "scan:v1", null, 1);
        assertVersionChange(baseline, facts(START, DEFINITION, ROOT, otherNative, otherExecution, history));
        for (var changed : List.of(
                nativeJob("externalWorker", "external-worker-complete", "exec-18", "proc-42", null, null,
                        null, "scan:v1", null, 1),
                nativeJob("externalWorker", "external-worker-complete", "exec-18", null, "exec-18", null,
                        null, "scan:v1", null, 1),
                nativeJob("externalWorker", "external-worker-complete", "exec-18", null, null, "bpmn",
                        null, "scan:v1", null, 1),
                nativeJob("externalWorker", "external-worker-complete", "exec-18", null, null, null,
                        "engine-def", "scan:v1", null, 1),
                nativeJob("externalWorker", "external-worker-complete", "exec-18", null, null, null,
                        null, "scan:v1", "native-correlation", 1))) {
            assertVersionChange(baseline, facts(START, DEFINITION, ROOT, changed, EXECUTION, history));
        }
        otherNative = nativeJob("externalWorker", "external-worker-complete", "exec-18", null, null, null,
                null, "rescan:v1", null, 1);
        var otherTopic = history.stream().map(row ->
                withDefinitionAndTopic(row, row.definitionId(), "rescan", row.modelSha256())).toList();
        assertVersionChange(baseline, facts(START, DEFINITION, ROOT, otherNative, EXECUTION, otherTopic));
        assertVersionChange(baseline, facts(List.of(earlier, evidence(2, "NEEDS_MANUAL", "RETRYABLE", "TIMEOUT_OTHER"))));
        assertVersionChange(baseline, facts(List.of(evidence(0, "RETRY_SCHEDULED", "RETRYABLE", "RETRY_OTHER"), last)));
    }

    private void assertVersionChange(Decision baseline, Facts changed) {
        var actual = specification.evaluate(changed);
        assertEquals(StatusEnum.RETRY_AVAILABLE, actual.status());
        assertNotEquals(baseline.version(), actual.version());
    }

    private static void assertManual(Decision actual, ReasonEnum reason, ReportedFailureEnum reported, String activity) {
        assertDisposition(actual, StatusEnum.MANUAL_REVIEW, reason, reported, activity);
    }

    private static void assertDisposition(Decision actual, StatusEnum status, ReasonEnum reason,
                                          ReportedFailureEnum reported, String activity) {
        assertEquals(status, actual.status());
        assertEquals(reason, actual.reason());
        assertEquals(reported, actual.reportedFailure());
        assertEquals(activity, actual.activityId());
        assertNull(actual.version());
        assertNull(actual.evidenceSha256());
        assertNull(actual.qualifying());
    }

    private Decision evaluate(LockedRoot root, RecoveryDeadletter nativeJob, List<RecoveryLeaseEvidence> history,
                              boolean collision, boolean alreadyAccepted, RecoveryProvenance provenance,
                              RecoveryBinding binding, boolean codeMatches) {
        return specification.evaluate(new Facts(START, DEFINITION, "job-18", root, nativeJob,
                nativeJob == null ? null : EXECUTION, collision, alreadyAccepted, history, () -> provenance,
                row -> codeMatches, (proof, row) -> binding));
    }

    private static Facts facts(List<RecoveryLeaseEvidence> history) {
        return facts(START, DEFINITION, ROOT, NATIVE, EXECUTION, history);
    }

    private static Facts facts(TaskOrigin start, Definition definition, LockedRoot root, RecoveryDeadletter nativeJob,
                               RecoveryExecution execution, List<RecoveryLeaseEvidence> history) {
        return new Facts(start, definition, "job-18", root, nativeJob, execution, false, false, history,
                WorkerRecoverySpecificationTest::ordinary, row -> true, (proof, row) -> binding());
    }

    private static TaskOrigin start(ProcessActor actor, String requestHash) {
        return new TaskOrigin(actor, START.commandId(), requestHash, START.releaseKey(), START.correlationId(),
                START.instanceId(), START.createdAt());
    }

    private static RecoveryProvenance ordinary() {
        var proof = mock(RecoveryProvenance.class);
        when(proof.ordinary()).thenReturn(true);
        return proof;
    }

    private static RecoveryBinding binding() {
        return RecoveryBinding.verified(WorkerEffect.READ_ONLY, ROOT.businessKey());
    }

    private static RecoveryDeadletter nativeJob(String type, String handler, String execution, String scope,
                                                String subScope, String scopeType, String scopeDefinition,
                                                String nativeTopic, String correlation, int revision) {
        return new RecoveryDeadletter("job-18", revision, type, "42", "proc-42", "engine-def", execution, "worker_1",
                scope, subScope, scopeType, scopeDefinition, correlation, handler, nativeTopic);
    }

    private static RecoveryLeaseEvidence evidence(int minute, String state, String kind, String code) {
        var leased = TIME.plusSeconds(minute * 60L);
        var expiry = leased.plusSeconds(30);
        var decided = switch (state) {
            case "EXPIRED" -> expiry;
            case "LEASED" -> null;
            default -> leased.plusSeconds(10);
        };
        boolean completed = "COMPLETED".equals(state);
        String result = kind != null ? ProcessRequestDigest.hash(List.of("worker-fail-v1", "job-18", "job-18", kind, code),
                Map.of()) : completed ? hash('f') : null;
        return new RecoveryLeaseEvidence(42, hash('a'), "worker-service", "scan", "v1", "job-18", "job-18",
                "BPMN", "proc-42", "release-42", "engine-def", "worker_1", workerOccurrenceKey(42, "job-18"), hash('c'),
                hash('d'), result, state, decided, kind, code, leased, expiry, completed);
    }

    private static RecoveryLeaseEvidence times(RecoveryLeaseEvidence row, Instant leased, Instant expires, Instant decided) {
        return new RecoveryLeaseEvidence(row.tenantId(), row.subjectIssuer(), row.subjectId(), row.topic(),
                row.schemaVersion(), row.jobId(), row.occurrenceId(), row.scopeType(), row.scopeId(), row.releaseKey(),
                row.definitionId(), row.elementId(), row.idempotencyKey(), row.modelSha256(), row.inputSha256(),
                row.resultSha256(), row.state(), decided, row.failureKind(), row.failureCode(), leased, expires, row.hasBusinessReceipt());
    }

    private static RecoveryLeaseEvidence withDefinitionAndTopic(RecoveryLeaseEvidence row, String definition,
                                                                String topic, String modelSha) {
        return new RecoveryLeaseEvidence(row.tenantId(), row.subjectIssuer(), row.subjectId(), topic,
                row.schemaVersion(), row.jobId(), row.occurrenceId(), row.scopeType(), row.scopeId(), row.releaseKey(),
                definition, row.elementId(), row.idempotencyKey(), modelSha, row.inputSha256(),
                row.resultSha256(), row.state(), row.decidedAt(), row.failureKind(), row.failureCode(),
                row.leasedAt(), row.leaseExpiresAt(), row.hasBusinessReceipt());
    }

    private static RecoveryLeaseEvidence withResult(RecoveryLeaseEvidence row, String result) {
        return new RecoveryLeaseEvidence(row.tenantId(), row.subjectIssuer(), row.subjectId(), row.topic(),
                row.schemaVersion(), row.jobId(), row.occurrenceId(), row.scopeType(), row.scopeId(), row.releaseKey(),
                row.definitionId(), row.elementId(), row.idempotencyKey(), row.modelSha256(), row.inputSha256(),
                result, row.state(), row.decidedAt(), row.failureKind(), row.failureCode(), row.leasedAt(),
                row.leaseExpiresAt(), row.hasBusinessReceipt());
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
