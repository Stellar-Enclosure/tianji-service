package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Effect strategies share the JDBC transaction and clock of the host's Worker execution store. */
@Slf4j
@Validated
@RequiredArgsConstructor
final class WorkerEffectReceiptStore {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final String RECOVERY_EXPIRED = "WORKER_RECOVERY_PROOF_EXPIRED";
    private static final String RECOVERY_MISMATCH = "WORKER_RECOVERY_PROOF_MISMATCH";
    private static final String UNKNOWN = "WORKER_EFFECT_UNKNOWN";
    private static final String COMPLETED = "COMPLETED";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String table;
    private final DataSource source;
    private final WorkerEffectOutcomeResolver resolver;
    private final Map<WorkerEffectSafetyEnum, EffectStrategy> strategies = createStrategies();

    private Map<WorkerEffectSafetyEnum, EffectStrategy> createStrategies() {
        var configured = new EnumMap<WorkerEffectSafetyEnum, EffectStrategy>(WorkerEffectSafetyEnum.class);
        configured.put(WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, this::executeTransactional);
        configured.put(WorkerEffectSafetyEnum.READ_ONLY, this::executeReadOnly);
        configured.put(WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, this::executeExternal);
        return Map.copyOf(configured);
    }

    WorkerExecutionReceipt execute(@Valid EffectExecutionVO execution) {
        var strategy = strategies.get(execution.registration().descriptor().effectMode());
        return strategy.execute(execution);
    }

    private WorkerExecutionReceipt executeTransactional(EffectExecutionVO execution) {
        return transaction.execute(status -> {
            lock(execution);
            var stored = find(execution);
            if (stored != null) return replay(execution, stored);
            if (execution.recovery() != null) throw unavailable(RECOVERY_MISMATCH);
            requireBoundTransaction();
            requireTime(execution);
            var result = validateResult(execution, execution.registration().transactional().execute(execution.context(), jdbc));
            requireBoundTransaction();
            if (status.isRollbackOnly()) throw new IllegalStateException("Worker effect transaction is rollback-only");
            requireTime(execution);
            return insertCompleted(execution, result);
        });
    }

    private WorkerExecutionReceipt executeReadOnly(EffectExecutionVO execution) {
        var known = transaction.execute(status -> {
            lock(execution);
            var existing = find(execution);
            return existing == null ? null : replay(execution, existing);
        });
        if (known != null) return known;
        requireTime(execution);
        var result = validateResult(execution,
                execution.registration().nonTransactional().execute(execution.context(), execution.context().effectKey()));
        return transaction.execute(status -> {
            lock(execution);
            var existing = find(execution);
            return existing == null ? insertCompleted(execution, result) : replay(execution, existing);
        });
    }

    private WorkerExecutionReceipt executeExternal(EffectExecutionVO execution) {
        var first = Objects.requireNonNull(transaction.execute(status -> {
            lock(execution);
            var existing = find(execution);
            if (existing != null) return inspect(execution, existing);
            if (execution.recovery() != null) throw unavailable(RECOVERY_MISMATCH);
            return new ExternalStepVO(null, insertStarted(execution), true);
        }));
        if (first.receipt() != null) return first.receipt();
        if (first.dispatch()) return dispatch(execution, first.row());

        var evidence = observe(execution, first.row());
        var next = Objects.requireNonNull(transaction.execute(status -> {
            lock(execution);
            var current = find(execution);
            if (current == null) throw conflict();
            var state = inspect(execution, current);
            if (state.receipt() != null) {
                if (current.generation() == evidence.dispatchGeneration() && evidence.status() == WorkerEffectEvidenceVO.Status.APPLIED
                        && !current.resultHash().equals(evidence.resultSha256())) throw conflict();
                return state;
            }
            if (!exact(current, first.row())) throw unavailable(recoveryMismatch(execution));
            var databaseNow = now();
            if (evidence.observedAt().isAfter(databaseNow) || !evidence.validUntil().isAfter(databaseNow)) {
                throw unavailable(execution.recovery() == null ? UNKNOWN : RECOVERY_EXPIRED);
            }
            if (evidence.status() == WorkerEffectEvidenceVO.Status.APPLIED) {
                var result = validateResult(execution, new AutomationWorkerBusinessResultDTO(evidence.businessReceiptId(), evidence.output()));
                if (!result.sha256().equals(evidence.resultSha256())) throw unavailable(recoveryMismatch(execution));
                requireFrozenResult(execution, result);
                return new ExternalStepVO(complete(execution, current, result, evidence, true), null, false);
            }
            if (evidence.status() != WorkerEffectEvidenceVO.Status.NOT_APPLIED_FINAL || execution.recovery() == null
                    || execution.recovery().mode() != WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED
                    || execution.recovery().observedGeneration() != current.generation()
                    || !"UNKNOWN".equals(current.state()) && !"STARTED".equals(current.state())) {
                throw unavailable(recoveryMismatch(execution));
            }
            return new ExternalStepVO(null, claim(execution, current, evidence), true);
        }));
        return next.receipt() != null ? next.receipt() : dispatch(execution, next.row(), evidence.validUntil());
    }

    private static String recoveryMismatch(EffectExecutionVO execution) {
        return execution.recovery() == null ? UNKNOWN : RECOVERY_MISMATCH;
    }

    private ExternalStepVO inspect(EffectExecutionVO execution, EffectRowVO row) {
        requireRecoveryGeneration(execution, row);
        return COMPLETED.equals(row.state())
                ? new ExternalStepVO(replay(execution, row), null, false)
                : new ExternalStepVO(null, row, false);
    }

    private WorkerEffectEvidenceVO observe(EffectExecutionVO execution, EffectRowVO row) {
        requireTime(execution);
        if (resolver == null || TransactionSynchronizationManager.isActualTransactionActive()) {
            throw unavailable(recoveryMismatch(execution));
        }
        var context = execution.context();
        var descriptor = execution.registration().descriptor();
        WorkerEffectEvidenceVO evidence;
        try {
            evidence = resolver.resolve(new WorkerEffectReconcileQuery(context.sourceId(), context.tenantId(), context.effectKey(),
                    execution.fingerprint(), descriptor.capabilityKey(), descriptor.capabilityVersion(), descriptor.handlerVersion(),
                    descriptor.targetId(), row.generation(), null));
        } catch (RuntimeException failure) {
            throw unavailable(recoveryMismatch(execution));
        }
        if (evidence == null || !context.effectKey().equals(evidence.effectKey())
                || !execution.fingerprint().equals(evidence.effectFingerprint()) || !descriptor.targetId().equals(evidence.targetId())
                || row.generation() != evidence.dispatchGeneration()) throw unavailable(recoveryMismatch(execution));
        return evidence;
    }

    private WorkerExecutionReceipt dispatch(EffectExecutionVO execution, EffectRowVO claim) {
        return dispatch(execution, claim, execution.context().deadlineAt());
    }

    private WorkerExecutionReceipt dispatch(EffectExecutionVO execution, EffectRowVO claim, Instant evidenceDeadline) {
        var alreadyCompleted = transaction.execute(status -> {
            lock(execution);
            var current = find(execution);
            if (current != null && COMPLETED.equals(current.state()) && current.generation() == claim.generation()) {
                return replay(execution, current);
            }
            if (current == null || !exact(current, claim) || !"STARTED".equals(current.state())) throw unavailable(UNKNOWN);
            if (!now().isBefore(evidenceDeadline)) throw unavailable(RECOVERY_EXPIRED);
            return null;
        });
        if (alreadyCompleted != null) return alreadyCompleted;
        requireTime(execution);
        if (!now().isBefore(evidenceDeadline)) throw unavailable(RECOVERY_EXPIRED);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable(UNKNOWN);
        try {
            var context = execution.context().forGeneration(claim.generation());
            var result = validateResult(execution, execution.registration().nonTransactional().execute(context, context.effectKey()));
            return transaction.execute(status -> {
                lock(execution);
                var current = find(execution);
                if (current != null && COMPLETED.equals(current.state()) && current.generation() == claim.generation()) {
                    if (!result.sha256().equals(current.resultHash())) throw conflict();
                    return replay(execution, current);
                }
                if (current == null || !exact(current, claim) || !"STARTED".equals(current.state())) throw unavailable(UNKNOWN);
                return complete(execution, current, result, null, false);
            });
        } catch (RuntimeException failure) {
            markUnknown(execution, claim);
            if (failure instanceof WorkerHandlerFailureException reported && reported.kind() == WorkerFailureKindEnum.UNKNOWN) throw reported;
            throw unavailable(UNKNOWN);
        }
    }

    private void markUnknown(EffectExecutionVO execution, EffectRowVO claim) {
        var context = execution.context();
        try {
            // No handler retry or generation change is possible from this best-effort bookkeeping update.
            transaction.executeWithoutResult(status -> jdbc.update("UPDATE " + table + """
                     SET state='UNKNOWN',revision=revision+1,updated_at=GREATEST(updated_at,clock_timestamp())
                     WHERE source_id=? AND tenant_id=? AND effect_key=? AND revision=? AND dispatch_generation=? AND state='STARTED'
                     """, context.sourceId(), context.tenantId(), context.effectKey(), claim.revision(), claim.generation()));
        } catch (RuntimeException failure) {
            // A durable STARTED row retains the same unresolved effect fence.
            log.debug("Worker unresolved effect remains in its durable dispatch state");
        }
    }

    private EffectRowVO insertStarted(EffectExecutionVO execution) {
        var descriptor = execution.registration().descriptor();
        var context = execution.context();
        var databaseNow = utc(now());
        var created = jdbc.queryForObject("INSERT INTO " + table + """
                (source_id,tenant_id,effect_key,capability_key,capability_version,input_schema_sha256,business_input_sha256,
                 state,revision,created_at,updated_at,output_schema_sha256,effect_mode,handler_version,target_id,
                 effect_fingerprint,dispatch_generation)
                VALUES (?,?,?,?,?,?,?,'STARTED',1,?,?,?, 'EXTERNAL_IDEMPOTENT',?,?,?,1) RETURNING created_at
                """, OffsetDateTime.class, context.sourceId(), context.tenantId(), context.effectKey(), descriptor.capabilityKey(),
                descriptor.capabilityVersion(), descriptor.inputSchemaSha256(), execution.businessInputHash(),
                databaseNow, databaseNow, descriptor.outputSchemaSha256(), descriptor.handlerVersion(), descriptor.targetId(), execution.fingerprint());
        requireTime(execution);
        return new EffectRowVO("STARTED", 1, 1, null, null, null, Objects.requireNonNull(created).toInstant());
    }

    private WorkerExecutionReceipt insertCompleted(EffectExecutionVO execution, EffectResultVO result) {
        var descriptor = execution.registration().descriptor();
        var context = execution.context();
        Instant committed = now();
        var rows = jdbc.update("INSERT INTO " + table + """
                (source_id,tenant_id,effect_key,capability_key,capability_version,input_schema_sha256,business_input_sha256,
                 state,result_json,business_receipt_id,revision,created_at,updated_at,output_schema_sha256,effect_mode,
                 handler_version,target_id,effect_fingerprint,result_sha256,dispatch_generation)
                VALUES (?,?,?,?,?,?,?,'COMPLETED',?::jsonb,?,1,?,?,?,?,?,?,?,?,1)
                """, context.sourceId(), context.tenantId(), context.effectKey(), descriptor.capabilityKey(),
                descriptor.capabilityVersion(), descriptor.inputSchemaSha256(), execution.businessInputHash(), json(result.output()),
                result.receipt(), utc(committed), utc(committed), descriptor.outputSchemaSha256(), descriptor.effectMode().name(),
                descriptor.handlerVersion(), descriptor.targetId(), execution.fingerprint(), result.sha256());
        if (rows != 1) throw conflict();
        requireTime(execution);
        return receipt(execution, committed, result, false);
    }

    private EffectRowVO claim(EffectExecutionVO execution, EffectRowVO prior, WorkerEffectEvidenceVO evidence) {
        var context = execution.context();
        var updated = now();
        if (prior.generation() >= ProcessTypedJson.V2_MAX_WIRE_INTEGER) throw unavailable(RECOVERY_MISMATCH);
        if (!updated.isBefore(evidence.validUntil())) throw unavailable(RECOVERY_EXPIRED);
        int changed = jdbc.update("UPDATE " + table + """
                 SET state='STARTED',revision=revision+1,dispatch_generation=dispatch_generation+1,updated_at=?,
                     last_reconciliation=?::jsonb,reconciled_at=?
                 WHERE source_id=? AND tenant_id=? AND effect_key=? AND revision=? AND dispatch_generation=?
                       AND state IN ('STARTED','UNKNOWN')
                """, utc(updated), reconciliation(evidence), utc(updated), context.sourceId(), context.tenantId(), context.effectKey(),
                prior.revision(), prior.generation());
        if (changed != 1) throw unavailable(RECOVERY_MISMATCH);
        requireTime(execution);
        return new EffectRowVO("STARTED", prior.revision() + 1, prior.generation() + 1, null, null, null, updated);
    }

    private WorkerExecutionReceipt complete(EffectExecutionVO execution, EffectRowVO prior, EffectResultVO result,
                                            WorkerEffectEvidenceVO evidence, boolean replayed) {
        var context = execution.context();
        Instant committed = now();
        String updateEvidence = evidence == null ? "" : ",last_reconciliation=?::jsonb,reconciled_at=?";
        var values = new java.util.ArrayList<Object>(List.of(result.receipt(), json(result.output()), result.sha256(), utc(committed)));
        if (evidence != null) {
            if (!committed.isBefore(evidence.validUntil())) {
                throw unavailable(execution.recovery() == null ? UNKNOWN : RECOVERY_EXPIRED);
            }
            values.add(reconciliation(evidence));
            values.add(utc(committed));
        }
        values.addAll(List.of(context.sourceId(), context.tenantId(), context.effectKey(), prior.revision(), prior.generation()));
        int changed = jdbc.update("UPDATE " + table
                + " SET state='COMPLETED',business_receipt_id=?,result_json=?::jsonb,result_sha256=?,revision=revision+1,updated_at=?"
                + updateEvidence + " WHERE source_id=? AND tenant_id=? AND effect_key=? AND revision=? AND dispatch_generation=?"
                + " AND state IN ('STARTED','UNKNOWN')", values.toArray());
        if (changed != 1) throw conflict();
        requireTime(execution);
        return receipt(execution, committed, result, replayed);
    }

    private static String reconciliation(WorkerEffectEvidenceVO evidence) {
        return json(Map.of("status", evidence.status().code(), "dispatchGeneration", evidence.dispatchGeneration(),
                "authorityRef", evidence.authorityRef(), "observedAt", evidence.observedAt().toString(),
                "validUntil", evidence.validUntil().toString(), "evidenceSha256", evidence.evidenceSha256()));
    }

    private static WorkerExecutionReceipt receipt(EffectExecutionVO execution, Instant committed, EffectResultVO result, boolean replayed) {
        return WorkerExecutionReceipt.fromAutomation(execution.registration().descriptor().handlerVersion(), committed,
                execution.fingerprint(), new AutomationWorkerBusinessResultDTO(result.receipt(), result.output()), result.sha256(), replayed);
    }

    private WorkerExecutionReceipt replay(EffectExecutionVO execution, EffectRowVO row) {
        if (!COMPLETED.equals(row.state())) throw unavailable(recoveryMismatch(execution));
        requireRecoveryGeneration(execution, row);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> output = JSON.readValue(row.output(), Map.class);
            var result = validateResult(execution, new AutomationWorkerBusinessResultDTO(row.receipt(), output));
            if (!row.resultHash().equals(result.sha256())) throw conflict();
            requireFrozenResult(execution, result);
            requireTime(execution);
            return receipt(execution, row.updatedAt(), result, true);
        } catch (IllegalArgumentException | tools.jackson.core.JacksonException malformed) {
            throw conflict();
        }
    }

    private static void requireRecoveryGeneration(EffectExecutionVO execution, EffectRowVO row) {
        var recovery = execution.recovery();
        if (recovery != null && !(row.generation() == recovery.observedGeneration()
                || recovery.mode() == WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED
                && row.generation() == recovery.observedGeneration() + 1)) throw unavailable(RECOVERY_MISMATCH);
    }

    private static void requireFrozenResult(EffectExecutionVO execution, EffectResultVO result) {
        var recovery = execution.recovery();
        if (recovery != null && recovery.mode() == WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED
                && (!recovery.businessReceiptId().equals(result.receipt()) || !recovery.resultSha256().equals(result.sha256()))) {
            throw unavailable(RECOVERY_MISMATCH);
        }
    }

    private static EffectResultVO validateResult(EffectExecutionVO execution, AutomationWorkerBusinessResultDTO result) {
        if (result == null) throw new IllegalArgumentException("Worker automation outcome is invalid");
        var output = execution.registration().normalizeOutput(result.getOutput());
        var receipt = result.getBusinessReceiptId();
        return new EffectResultVO(receipt, output, AutomationWorkerBusinessResultDTO.resultSha256(execution.fingerprint(), receipt, output));
    }

    private EffectRowVO find(EffectExecutionVO execution) {
        var context = execution.context();
        var rows = jdbc.query("SELECT * FROM " + table + " WHERE source_id=? AND tenant_id=? AND effect_key=?",
                (rs, row) -> read(execution, rs), context.sourceId(), context.tenantId(), context.effectKey());
        if (rows.size() > 1) throw conflict();
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static EffectRowVO read(EffectExecutionVO execution, ResultSet row) throws SQLException {
        var descriptor = execution.registration().descriptor();
        if (!descriptor.capabilityKey().equals(row.getString("capability_key"))
                || descriptor.capabilityVersion() != row.getLong("capability_version")
                || !descriptor.inputSchemaSha256().equals(row.getString("input_schema_sha256"))
                || !descriptor.outputSchemaSha256().equals(row.getString("output_schema_sha256"))
                || !descriptor.effectMode().name().equals(row.getString("effect_mode"))
                || !descriptor.handlerVersion().equals(row.getString("handler_version"))
                || !Objects.equals(descriptor.targetId(), row.getString("target_id"))
                || !execution.fingerprint().equals(row.getString("effect_fingerprint"))
                || !execution.businessInputHash().equals(row.getString("business_input_sha256"))) throw conflict();
        long generation = row.getLong("dispatch_generation");
        requireStoredReconciliation(row, generation);
        return new EffectRowVO(row.getString("state"), row.getLong("revision"), generation,
                row.getString("business_receipt_id"), row.getString("result_json"), row.getString("result_sha256"),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static void requireStoredReconciliation(ResultSet row, long generation) throws SQLException {
        var evidence = row.getString("last_reconciliation");
        if (evidence == null) {
            if (generation != 1) throw conflict();
            return;
        }
        try {
            var node = JSON.readTree(evidence);
            long observed = node.path("dispatchGeneration").longValue();
            String status = node.path("status").asText();
            if ("APPLIED".equals(status)) {
                if (observed != generation || !COMPLETED.equals(row.getString("state"))) throw conflict();
            } else if (!"NOT_APPLIED_FINAL".equals(status) || observed != generation - 1) {
                throw conflict();
            }
        } catch (tools.jackson.core.JacksonException malformed) {
            throw conflict();
        }
    }

    private static boolean exact(EffectRowVO current, EffectRowVO expected) {
        return current.revision() == expected.revision() && current.generation() == expected.generation();
    }

    private void lock(EffectExecutionVO execution) {
        requireBoundTransaction();
        var current = now();
        requireTime(execution, current);
        var lockMillis = Math.max(1, Duration.between(current, execution.context().deadlineAt()).toMillis());
        var context = execution.context();
        var key = WorkerDigests.hash(List.of("worker-automation-effect-lock-v2", context.sourceId(),
                Long.toString(context.tenantId()), context.effectKey()), Map.of());
        jdbc.queryForObject("SELECT set_config('lock_timeout', ?, true)", String.class, lockMillis + "ms");
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "tianji:business-worker:" + key);
        requireTime(execution);
    }

    private void requireBoundTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Worker effect transaction is not bound to its DataSource");
        }
    }

    private void requireTime(EffectExecutionVO execution) {
        requireTime(execution, now());
    }

    private static void requireTime(EffectExecutionVO execution, Instant databaseNow) {
        var recovery = execution.recovery();
        if (recovery != null && (recovery.observedAt().isAfter(databaseNow) || !databaseNow.isBefore(recovery.validUntil()))) {
            throw unavailable(RECOVERY_EXPIRED);
        }
        if (!databaseNow.isBefore(execution.context().deadlineAt())) throw new WorkerExecutionDeadlineException();
    }

    Instant now() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class)).toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String json(Map<String, Object> value) {
        return new String(ProcessTypedJson.canonicalBytes(value), StandardCharsets.UTF_8);
    }

    private static WorkerExecutionConflictException conflict() {
        return new WorkerExecutionConflictException();
    }

    private static WorkerHandlerFailureException unavailable(String code) {
        return new WorkerHandlerFailureException(WorkerFailureKindEnum.UNKNOWN, code);
    }

    record EffectExecutionVO(AutomationWorkerHandlerRegistration registration, AutomationWorkerExecutionContextVO context,
                             String fingerprint, String businessInputHash, WorkerEffectRecoveryVO recovery) { }

    @FunctionalInterface
    private interface EffectStrategy {
        WorkerExecutionReceipt execute(EffectExecutionVO execution);
    }

    private record EffectResultVO(String receipt, Map<String, Object> output, String sha256) { }

    private record EffectRowVO(String state, long revision, long generation, String receipt, String output,
                               String resultHash, Instant updatedAt) { }

    private record ExternalStepVO(WorkerExecutionReceipt receipt, EffectRowVO row, boolean dispatch) { }
}
