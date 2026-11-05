package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.cfg.TransactionPropagation;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.interceptor.CommandContextInterceptor;
import org.flowable.common.engine.impl.interceptor.LogInterceptor;
import org.flowable.common.engine.impl.interceptor.TransactionContextInterceptor;
import org.flowable.common.spring.SpringTransactionInterceptor;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.ProcessMigrationServiceImpl;
import org.flowable.engine.impl.interceptor.BpmnOverrideContextInterceptor;
import org.flowable.engine.impl.interceptor.CommandInvoker;
import org.flowable.engine.impl.migration.ProcessInstanceMigrationManagerImpl;
import org.flowable.engine.migration.ActivityMigrationMapping;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessReleaseQuery;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService.ProcessMigrationPreflightStatusEnum.*;
import static top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type.BPMN;

/** One owner-local structural observation. Live activation and native state changes have separate owners. */
public final class ProcessMigrationPreflightService {
    private static final List<Class<?>> PINNED_COMMAND_CHAIN = List.of(LogInterceptor.class, SpringTransactionInterceptor.class,
            CommandContextInterceptor.class, TransactionContextInterceptor.class, BpmnOverrideContextInterceptor.class, CommandInvoker.class);
    private final ProcessEngine process;
    private final SpringProcessEngineConfiguration engine;
    private final ProcessReleaseService releases;
    private final ProcessModelValidator validator;
    private final VerifiedProcessModels models;
    private final TianjiProcessAccess access;
    private final ProcessControlNativeAccess nativeAccess;
    private final ProcessCommandReceiptStore receipts;
    private final ProcessApiSchemaVerifier verifier;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate read;
    private final String schema;

    public ProcessMigrationPreflightService(ProcessEngine process, SpringDmnEngineConfiguration dmn,
                                            ProcessReleaseService releases, ProcessModelValidator validator,
                                            TianjiProcessAccess access) {
        this.process = Objects.requireNonNull(process, "process");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.access = Objects.requireNonNull(access, "access");
        if (!(process.getProcessEngineConfiguration() instanceof SpringProcessEngineConfiguration configured)
                || !(configured.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || manager.getDataSource() == null) throw unavailable();
        DataSource source = configured.getDataSource();
        if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
        if (source != manager.getDataSource() || !releases.usesStorage(configured, source, manager)) throw unavailable();
        engine = configured;
        requirePinnedValidation();

        nativeAccess = new ProcessControlNativeAccess(source, engine);
        schema = engine.getDatabaseSchema();
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(2);
        receipts = new ProcessCommandReceiptStore(jdbc, schema);
        verifier = new ProcessApiSchemaVerifier(source, schema);
        models = new VerifiedProcessModels(engine, dmn, validator);
        read = new TransactionTemplate(manager);
        // Keep primary routing. The owning native adapter sets physical read-only before business SQL.
        read.setReadOnly(false);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(2);
    }

    public void verifyReadiness() {
        requireNoAmbientTransaction();
        try {
            read.executeWithoutResult(status -> {
                nativeAccess.beginMigrationObservation();
                verifier.verifyAgentResultInbox();
                if (!verifier.hasCaseMigration() || !Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM \"" + schema + "\".tianji_process_schema_history WHERE version='10')",
                        Boolean.class))) throw unavailable();
                nativeAccess.verifyMigrationReadCoverage();
                nativeAccess.requireMigrationReadOnly();
            });
        } catch (DataAccessException | TransactionException | IllegalArgumentException | IllegalStateException failure) {
            throw unavailable();
        }
    }

    public ProcessMigrationPreflightDTO inspect(ValidateProcessMigrationQuery query) {
        return inspectBoundOwner(query, null);
    }

    public ProcessMigrationPreflightDTO inspectForResourceActor(ProcessActor resourceActor, ValidateProcessMigrationQuery query) {
        if (resourceActor == null || "LEGACY".equals(resourceActor.issuerFingerprint())) {
            throw new AccessDeniedException("Verified Process owner is required");
        }
        return inspectBoundOwner(query, resourceActor);
    }

    private ProcessMigrationPreflightDTO inspectBoundOwner(ValidateProcessMigrationQuery query, ProcessActor resourceActor) {
        if (query == null) throw new IllegalArgumentException("Process migration preflight query is invalid");
        requireNoAmbientTransaction();
        ProcessActor actor = access.require(TianjiProcessPermissions.GET_API);
        ProcessActor releaseReader = access.require(TianjiProcessPermissions.RELEASE_GET_API);
        if (actor == null || !actor.equals(releaseReader) || "LEGACY".equals(actor.issuerFingerprint())
                || resourceActor != null && !actor.equals(resourceActor)) {
            throw new AccessDeniedException("Verified Process owner is required");
        }
        try {
            return Objects.requireNonNull(read.execute(status -> inspect(actor, query)));
        } catch (MissingStart failure) {
            throw new ProcessResourceNotFoundException();
        } catch (DataAccessException | TransactionException | FlowableException
                 | IllegalStateException | IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    private ProcessMigrationPreflightDTO inspect(ProcessActor actor, ValidateProcessMigrationQuery query) {
        Instant observedAt = nativeAccess.beginMigrationObservation();
        nativeAccess.verifyMigrationReadCoverage();
        TaskOrigin start;
        try {
            start = receipts.readStartByInstance(actor, query.instanceId());
        } catch (ProcessResourceNotFoundException missing) {
            throw new MissingStart();
        }
        if (!actor.equals(start.requester()) || !query.instanceId().equals(start.instanceId())) throw new MissingStart();
        var root = nativeAccess.readMigrationRoot(start).orElse(null);
        if (root == null || root.suspensionState() != 1) return report(start, query, INSTANCE_NOT_LIVE, null, observedAt);

        var sourceRelease = releases.readMigrationRelease(actor, start.releaseKey())
                .orElseThrow(ProcessMigrationPreflightService::unavailable);
        if (!start.releaseKey().equals(sourceRelease.releaseKey())) throw unavailable();
        var targetRelease = releases.readMigrationRelease(actor, query.targetReleaseVersion()).orElse(null);
        if (targetRelease == null) return report(start, query, TARGET_UNAVAILABLE, null, observedAt);
        if (!query.targetReleaseVersion().equals(targetRelease.releaseKey())) throw unavailable();
        if (!query.expectedTargetManifestSha256().equals(targetRelease.manifestSha256())) {
            return report(start, query, TARGET_PIN_MISMATCH, null, observedAt);
        }

        Definition source = onlyBpmn(sourceRelease);
        Definition target = onlyBpmn(targetRelease);
        if (source == null || target == null || !source.engineDefinitionId().equals(root.engineDefinitionId())
                || sourceRelease.releaseKey().equals(targetRelease.releaseKey())
                || !source.definitionKey().equals(target.definitionKey()) || target.engineVersion() <= source.engineVersion()
                || source.engineDefinitionId().equals(target.engineDefinitionId())
                || source.engineDeploymentId().equals(target.engineDeploymentId())) {
            return report(start, query, RELEASE_PAIR_UNSUPPORTED, null, observedAt);
        }
        var sourceModel = models.process(actor.tenantId(), source);
        var targetModel = models.process(actor.tenantId(), target);
        if (!releases.verifiesSingleBpmnManifest(sourceRelease, source, sourceModel)
                || !releases.verifiesSingleBpmnManifest(targetRelease, target, targetModel)) throw unavailable();
        var sourceTimer = validator.plainMigrationTimer(sourceModel).orElse(null);
        var targetTimer = validator.plainMigrationTimer(targetModel).orElse(null);
        if (sourceTimer == null || !sourceTimer.equals(targetTimer)) {
            return report(start, query, SHAPE_UNSUPPORTED, null, observedAt);
        }
        String activityId = sourceTimer.activityId();
        if (!nativeAccess.hasExactPlainTimer(start, root, activityId, observedAt)) {
            return report(start, query, ACTIVE_EVIDENCE_UNSAFE, null, observedAt);
        }

        requirePinnedValidation();
        var migration = process.getProcessMigrationService();
        if (migration == null || migration.getClass() != ProcessMigrationServiceImpl.class
                || migration != engine.getProcessMigrationService()) throw unavailable();
        var validation = migration.createProcessInstanceMigrationBuilder()
                .migrateToProcessDefinition(target.engineDefinitionId())
                .addActivityMigrationMapping(ActivityMigrationMapping.createMappingFor(activityId, activityId))
                .validateMigration(start.instanceId());
        nativeAccess.requireMigrationReadOnly();
        if (validation == null) throw unavailable();
        return report(start, query, validation.isMigrationValid() ? NATIVE_STRUCTURE_VALIDATED_ONLY : NATIVE_REJECTED,
                activityId, observedAt);
    }

    private void requirePinnedValidation() {
        var migrationManager = engine.getProcessInstanceMigrationManager();
        var command = engine.getDefaultCommandConfig();
        var interceptors = engine.getCommandInterceptors();
        if (migrationManager == null || migrationManager.getClass() != ProcessInstanceMigrationManagerImpl.class
                || command == null || command.getTransactionPropagation() != TransactionPropagation.REQUIRED
                || interceptors == null || !interceptors.stream().map(Object::getClass).toList().equals(PINNED_COMMAND_CHAIN)
                || present(engine.getCustomPreCommandInterceptors()) || present(engine.getCustomPostCommandInterceptors())
                || present(engine.getCustomMybatisInterceptors())) throw unavailable();
    }

    private static boolean present(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static Definition onlyBpmn(ProcessReleaseService.Release release) {
        if (release.definitions().size() != 1) return null;
        var definition = release.definitions().getFirst();
        return definition.type() == BPMN && definition.inputs().isEmpty() ? definition : null;
    }

    private static ProcessMigrationPreflightDTO report(TaskOrigin start, ValidateProcessMigrationQuery query,
                                                        ProcessMigrationPreflightStatusEnum status, String activityId,
                                                        Instant observedAt) {
        return new ProcessMigrationPreflightDTO(start.instanceId(), start.releaseKey(), query.targetReleaseVersion(),
                status, activityId, observedAt);
    }

    private static void requireNoAmbientTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive() || Context.getCommandContext() != null
                || Thread.currentThread().isInterrupted()) {
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process migration preflight source is unavailable");
    }

    private static final class MissingStart extends RuntimeException { }

    public record ValidateProcessMigrationQuery(String instanceId, String targetReleaseVersion,
                                                String expectedTargetManifestSha256) {
        public ValidateProcessMigrationQuery {
            instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
            targetReleaseVersion = new GetProcessReleaseQuery(targetReleaseVersion).releaseVersion();
            if (expectedTargetManifestSha256 == null || !expectedTargetManifestSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Process migration preflight query is invalid");
            }
        }
    }

    public record ProcessMigrationPreflightDTO(String instanceId, String sourceReleaseVersion,
                                               String targetReleaseVersion, ProcessMigrationPreflightStatusEnum status,
                                               String activityId, Instant observedAt) { }

    public enum ProcessMigrationPreflightStatusEnum {
        NATIVE_STRUCTURE_VALIDATED_ONLY, INSTANCE_NOT_LIVE, TARGET_UNAVAILABLE, TARGET_PIN_MISMATCH,
        RELEASE_PAIR_UNSUPPORTED, SHAPE_UNSUPPORTED, ACTIVE_EVIDENCE_UNSAFE, NATIVE_REJECTED
    }
}
