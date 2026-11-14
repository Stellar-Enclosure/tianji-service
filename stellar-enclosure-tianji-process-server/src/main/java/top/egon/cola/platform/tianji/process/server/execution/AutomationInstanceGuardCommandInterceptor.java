package top.egon.cola.platform.tianji.process.server.execution;

import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.impl.interceptor.AbstractCommandInterceptor;
import org.flowable.common.engine.impl.interceptor.Command;
import org.flowable.common.engine.impl.interceptor.CommandConfig;
import org.flowable.common.engine.impl.interceptor.CommandExecutor;
import org.flowable.engine.impl.cmd.AbstractSetProcessInstanceStateCmd;
import org.flowable.engine.impl.cmd.ChangeActivityStateCmd;
import org.flowable.engine.impl.cmd.DeleteProcessInstanceCmd;
import org.flowable.engine.impl.cmd.DeleteProcessInstancesByIdCmd;
import org.flowable.engine.impl.cmd.DeleteTaskCmd;
import org.flowable.engine.impl.cmd.NeedsActiveExecutionCmd;
import org.flowable.engine.impl.cmd.NeedsActiveTaskCmd;
import org.flowable.engine.impl.cmd.SignalEventReceivedCmd;
import org.flowable.engine.impl.cmd.RescheduleTimerJobCmd;
import org.flowable.engine.impl.cmd.JobRetryCmd;
import org.flowable.engine.impl.jobexecutor.DefaultFailedJobCommandFactory;
import org.flowable.engine.impl.runtime.ChangeActivityStateBuilderImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.job.api.Job;
import org.flowable.job.service.impl.cmd.ExecuteAsyncRunnableJobCmd;
import org.flowable.job.service.impl.cmd.CancelJobsCmd;
import org.flowable.job.service.impl.cmd.ExecuteJobCmd;
import org.flowable.job.service.impl.cmd.MoveTimerToExecutableJobCmd;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class AutomationInstanceGuardCommandInterceptor extends AbstractCommandInterceptor {
    private static final String ATTRIBUTE = AutomationInstanceGuardCommandInterceptor.class.getName();
    private static final String GUARDED_JOB = ATTRIBUTE + ".job";
    private static final String DEFERRED_RETRY = ATTRIBUTE + ".deferredRetry";
    private static final Object TX_MARKER = new Object();
    private static final String ENGINE_COMMAND = "org.flowable.engine.impl.cmd.";
    private static final String JOB_COMMAND = "org.flowable.job.service.impl.cmd.";
    private static final String AUTOMATION_NAMESPACE = "urn:stellar-enclosure:tianji:automation:2";
    private static final Set<String> JOB_MUTATIONS = Set.of("ExecuteAsyncJobCmd", "DeleteJobCmd", "DeleteTimerJobCmd",
            "DeleteDeadLetterJobCmd", "DeleteSuspendedJobCmd", "DeleteExternalWorkerJobCmd", "MoveJobToDeadLetterJobCmd",
            "MoveDeadLetterJobToExecutableJobCmd", "MoveSuspendedJobToExecutableJobCmd", "SetJobRetriesCmd",
            "SetTimerJobRetriesCmd");
    private static final Set<String> EXCLUSIVE_JOBS = Set.of("LockExclusiveJobCmd", "UnlockExclusiveJobCmd");
    private static final Set<String> PROCESS_MUTATIONS = Set.of("ProcessInstanceMigrationCmd", "SetProcessInstanceBusinessKeyCmd",
            "SetProcessInstanceBusinessStatusCmd", "SetProcessInstanceNameCmd");
    private static final Set<String> READ_EXECUTION_COMMANDS = Set.of("GetExecutionVariablesCmd", "GetExecutionVariableInstancesCmd",
            "GetExecutionVariableCmd", "GetExecutionVariableInstanceCmd", "HasExecutionVariableCmd", "GetExecutionsVariablesCmd");
    private static final Set<String> READ_TASK_COMMANDS = Set.of("GetTaskVariableCmd", "GetTaskVariablesCmd", "GetTaskVariableInstanceCmd",
            "GetTaskVariableInstancesCmd", "HasTaskVariableCmd", "GetTaskDataObjectCmd", "GetTaskDataObjectsCmd", "GetTaskFormCmd",
            "GetTaskFormModelCmd", "GetRenderedTaskFormCmd", "GetTasksLocalVariablesCmd");
    private static final Field DELETE_ID = field(DeleteProcessInstanceCmd.class, "processInstanceId");
    private static final Field DELETE_IDS = field(DeleteProcessInstancesByIdCmd.class, "processInstanceIds");
    private static final Field TASK_ID = field(NeedsActiveTaskCmd.class, "taskId");
    private static final Field TASK_DELETE_ID = field(DeleteTaskCmd.class, "taskId");
    private static final Field TASK_DELETE_IDS = field(DeleteTaskCmd.class, "taskIds");
    private static final Field EXECUTION_ID = field(NeedsActiveExecutionCmd.class, "executionId");
    private static final Field SIGNAL_ID = field(SignalEventReceivedCmd.class, "executionId");
    private static final Field SIGNAL_NAME = field(SignalEventReceivedCmd.class, "eventName");
    private static final Field TIMER_ID = field(RescheduleTimerJobCmd.class, "timerJobId");
    private static final Field RETRY_ID = field(JobRetryCmd.class, "jobId");
    private static final Field CANCEL_JOB_IDS = field(CancelJobsCmd.class, "jobIds");
    private static final Field PROCESS_STATE_ID = field(AbstractSetProcessInstanceStateCmd.class, "processInstanceId");
    private final JdbcTemplate jdbc;
    private final String schema;
    private final ProcessInstanceBindingStore bindings;

    public AutomationInstanceGuardCommandInterceptor(JdbcTemplate jdbc, String schema, ProcessInstanceBindingStore bindings) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.bindings = Objects.requireNonNull(bindings);
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Invalid Process schema");
        this.schema = schema;
    }

    @Override
    public <T> T execute(CommandConfig config, Command<T> command, CommandExecutor executor) {
        var addresses = affected(command);
        if (addresses.isEmpty()) return next.execute(config, command, executor);
        var roots = new LinkedHashSet<RootVO>();
        for (var address : addresses) locate(address).ifPresent(roots::add);
        if (roots.isEmpty()) return next.execute(config, command, executor);
        if (roots.size() > 1 || addresses.size() > 1) {
            throw new IllegalArgumentException("Automation mutations must target one root instance");
        }
        var root = roots.iterator().next();
        lockKnownRoot(root.tenantId(), root.instanceId());
        var context = CommandContextUtil.getCommandContext();
        if (context == null) throw unavailable();
        context.addAttribute(ATTRIBUTE, root);
        if (command instanceof ExecuteJobCmd || command instanceof ExecuteAsyncRunnableJobCmd
                || (JOB_COMMAND + "ExecuteAsyncJobCmd").equals(command.getClass().getName())) {
            context.addAttribute(GUARDED_JOB, addresses.getFirst().id());
        }
        return next.execute(config, command, executor);
    }

    public static void registerFailedJobHandling(SpringProcessEngineConfiguration engine) {
        var delegate = engine.getFailedJobCommandFactory() == null
                ? new DefaultFailedJobCommandFactory() : engine.getFailedJobCommandFactory();
        engine.setFailedJobCommandFactory((jobId, failure) -> {
            var command = delegate.getCommand(jobId, failure);
            var context = CommandContextUtil.getCommandContext();
            if (context == null || !jobId.equals(context.getAttribute(GUARDED_JOB))) return command;
            if (!(context.getAttribute(ATTRIBUTE) instanceof RootVO root) || command.getClass() != JobRetryCmd.class) {
                throw unavailable();
            }
            requireHeld(root.tenantId(), root.instanceId());
            if (!jobId.equals(context.getAttribute(DEFERRED_RETRY))) {
                context.addAttribute(DEFERRED_RETRY, jobId);
                // Flowable runs failure bookkeeping in REQUIRES_NEW before the command's transaction rolls back.
                // Retry updates a native execution, so it must first get the root lock in its own transaction.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_ROLLED_BACK) return;
                        try {
                            var executor = engine.getCommandExecutor();
                            var config = executor.getDefaultConfig().transactionRequiresNew();
                            executor.execute(config, command);
                        } catch (RuntimeException retryFailure) {
                            log.error("Flowable failed-job accounting did not commit for Automation ({})",
                                    retryFailure.getClass().getSimpleName());
                        }
                    }
                });
            }
            return ignored -> null;
        });
    }

    public AutomationInstanceBindingBO lockKnownRoot(long tenantId, String instanceId) {
        requireOwningTransaction();
        var expected = new RootVO(tenantId, instanceId);
        RootVO held = (RootVO) TransactionSynchronizationManager.getResource(TX_MARKER);
        if (held != null && !expected.equals(held)) throw new IllegalArgumentException("Automation mutations must target one root instance");
        if (held == null) {
            long timeout = Objects.requireNonNull(jdbc.queryForObject(
                    "SELECT setting::bigint FROM pg_catalog.pg_settings WHERE name='lock_timeout'", Long.class));
            if (timeout == 0 || timeout > 5_000) {
                jdbc.queryForObject("SELECT pg_catalog.set_config('lock_timeout','5000ms',true)", String.class);
            }
        }
        var binding = bindings.lockRoot(tenantId, instanceId);
        if (held == null) {
            TransactionSynchronizationManager.bindResource(TX_MARKER, expected);
            TransactionSynchronizationManager.registerSynchronization(new RootMarker(expected));
        }
        return binding;
    }

    public static void requireHeld(long tenantId, String instanceId) {
        var expected = new RootVO(tenantId, instanceId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !expected.equals(TransactionSynchronizationManager.getResource(TX_MARKER))) throw unavailable();
    }

    private void requireOwningTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) throw unavailable();
    }

    private Optional<RootVO> locate(AddressVO address) {
        if (!valid(address.id())) return Optional.empty();
        String execution = schema + ".act_ru_execution";
        String definition = schema + ".tianji_process_release_definition";
        String automationRelease = schema + ".tianji_process_automation_release";
        String instances = schema + ".tianji_process_instance_binding";
        String selection = switch (address.kind()) {
            case EXECUTION -> "SELECT id_ AS execution_id, proc_inst_id_ AS process_id FROM " + execution + " WHERE id_=?";
            case TASK -> "SELECT execution_id_ AS execution_id,proc_inst_id_ AS process_id FROM " + schema + ".act_ru_task WHERE id_=?";
            case JOB -> jobLocator();
        };
        boolean hasV14 = Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_catalog.to_regclass(?) IS NOT NULL", Boolean.class, instances));
        String extensions = hasV14 ? """
                LEFT JOIN %s published ON published.tenant_id=native_def.tenant_id AND published.release_key=native_def.release_key
                LEFT JOIN %s bound ON bound.instance_id=root.id_ AND bound.tenant_id::text=root.tenant_id_
                """.formatted(automationRelease, instances) : "";
        String sql = """
                WITH native_target AS (%s)
                SELECT root.id_, root.tenant_id_, %s AS has_binding, %s AS has_release, native_def.tenant_id::text,
                       COALESCE(native_def.input_schema=pg_catalog.jsonb_build_object('automationBindingRef','STRING'),false),
                       root.proc_def_id_, native_def.definition_key
                FROM native_target t
                LEFT JOIN %s child ON child.id_=COALESCE(t.execution_id,t.process_id)
                JOIN %s root ON root.id_=COALESCE(child.root_proc_inst_id_,child.proc_inst_id_,t.process_id)
                LEFT JOIN %s native_def ON native_def.engine_definition_id=root.proc_def_id_ AND native_def.model_type='BPMN'
                %s
                """.formatted(selection, hasV14 ? "bound.instance_id IS NOT NULL" : "FALSE",
                hasV14 ? "published.release_key IS NOT NULL" : "FALSE",
                execution, execution, definition, extensions);
        var found = jdbc.query(sql, (row, index) -> new NativeRootVO(row.getString(1), row.getString(2), row.getBoolean(3),
                row.getBoolean(4), row.getString(5), row.getBoolean(6), row.getString(7), row.getString(8)),
                address.kind() == Kind.JOB ? new Object[]{address.id(),address.id(),address.id(),address.id(),address.id()}
                : new Object[]{address.id()});
        if (found.size() > 1) throw unavailable();
        return found.stream().findFirst().flatMap(this::automationRoot);
    }

    private Optional<RootVO> automationRoot(NativeRootVO nativeRoot) {
        boolean automation = nativeRoot.hasRelease() || nativeRoot.ambiguousInputs() && hasAutomationModelMarker(nativeRoot);
        if (!automation && !nativeRoot.hasBinding()) return Optional.empty();
        var tenant = nativeRoot.tenant();
        if (!automation || !nativeRoot.hasBinding() || tenant == null || !tenant.matches("[1-9][0-9]{0,18}")
                || !tenant.equals(nativeRoot.definitionTenant())) throw unavailable();
        try {
            return Optional.of(new RootVO(Long.parseLong(tenant), nativeRoot.instanceId()));
        } catch (NumberFormatException badTenant) {
            throw unavailable();
        }
    }

    private boolean hasAutomationModelMarker(NativeRootVO root) {
        var configuration = CommandContextUtil.getProcessEngineConfiguration();
        if (configuration == null || root.definitionId() == null || root.definitionKey() == null) throw unavailable();
        // The legacy publisher accepts a single input with this name. The native model disambiguates it.
        var model = configuration.getRepositoryService().getBpmnModel(root.definitionId());
        if (model == null) throw unavailable();
        var process = model.getProcessById(root.definitionKey());
        if (process == null) throw unavailable();
        return "AUTOMATION_V2".equals(process.getAttributeValue(AUTOMATION_NAMESPACE, "profile"));
    }

    private String jobLocator() {
        return List.of("act_ru_job", "act_ru_timer_job", "act_ru_suspended_job", "act_ru_deadletter_job", "act_ru_external_job").stream()
                .map(table -> "SELECT execution_id_ AS execution_id,process_instance_id_ AS process_id FROM " + schema + "." + table
                        + " WHERE id_=? AND (scope_type_ IS NULL OR scope_type_='bpmn')").reduce((left, right) -> left + " UNION ALL " + right)
                .orElseThrow();
    }

    private List<AddressVO> affected(Command<?> command) {
        if (command instanceof ExecuteJobCmd job) return one(Kind.JOB, job.getJobId());
        if (command instanceof ExecuteAsyncRunnableJobCmd job) return one(Kind.JOB, job.getJobId());
        if (command instanceof MoveTimerToExecutableJobCmd job) return one(Kind.JOB, job.getJobId());
        if (command instanceof DeleteProcessInstanceCmd) return one(Kind.EXECUTION, value(DELETE_ID, command));
        if (command instanceof DeleteProcessInstancesByIdCmd) return multiple(Kind.EXECUTION, value(DELETE_IDS, command));
        if (command instanceof ChangeActivityStateCmd) {
            var builder = (ChangeActivityStateBuilderImpl) value(field(ChangeActivityStateCmd.class, "changeActivityStateBuilder"), command);
            return one(Kind.EXECUTION, builder.getProcessInstanceId());
        }
        if (command instanceof AbstractSetProcessInstanceStateCmd) return one(Kind.EXECUTION, value(PROCESS_STATE_ID, command));
        if (command instanceof DeleteTaskCmd) {
            var single = value(TASK_DELETE_ID, command);
            return single == null ? multiple(Kind.TASK, value(TASK_DELETE_IDS, command)) : one(Kind.TASK, single);
        }
        if (command instanceof SignalEventReceivedCmd) {
            var target = value(SIGNAL_ID, command);
            if (target == null) rejectAutomationSignalBroadcast((String) value(SIGNAL_NAME, command));
            return one(Kind.EXECUTION, target);
        }
        if (command instanceof RescheduleTimerJobCmd) return one(Kind.JOB, value(TIMER_ID, command));
        if (command instanceof JobRetryCmd) return one(Kind.JOB, value(RETRY_ID, command));
        if (command instanceof CancelJobsCmd) return multiple(Kind.JOB, value(CANCEL_JOB_IDS, command));
        String name = command.getClass().getSimpleName();
        if (command instanceof NeedsActiveExecutionCmd<?> && !READ_EXECUTION_COMMANDS.contains(name)) {
            return one(Kind.EXECUTION, value(EXECUTION_ID, command));
        }
        if (command instanceof NeedsActiveTaskCmd<?> && !READ_TASK_COMMANDS.contains(name)) return one(Kind.TASK, value(TASK_ID, command));
        if (command instanceof org.flowable.engine.impl.cmd.AbstractExternalWorkerJobCmd) {
            return one(Kind.JOB, value(field(org.flowable.engine.impl.cmd.AbstractExternalWorkerJobCmd.class, "externalJobId"), command));
        }
        if (command instanceof org.flowable.job.service.impl.cmd.AbstractExternalWorkerJobCmd) {
            return one(Kind.JOB, value(field(org.flowable.job.service.impl.cmd.AbstractExternalWorkerJobCmd.class, "externalJobId"), command));
        }
        if ((JOB_COMMAND + name).equals(command.getClass().getName()) && JOB_MUTATIONS.contains(name)) {
            return one(Kind.JOB, value(field(command.getClass(), "jobId"), command));
        }
        if ((JOB_COMMAND + name).equals(command.getClass().getName()) && EXCLUSIVE_JOBS.contains(name)) {
            var job = (Job) value(field(command.getClass(), "job"), command);
            if (job == null || job.getScopeType() != null && !"bpmn".equals(job.getScopeType())) return List.of();
            // A successful async execution may already have removed the job before the unlock command.
            return one(Kind.EXECUTION, job.getProcessInstanceId() == null ? job.getExecutionId() : job.getProcessInstanceId());
        }
        if ((ENGINE_COMMAND + name).equals(command.getClass().getName()) && PROCESS_MUTATIONS.contains(name)) {
            return one(Kind.EXECUTION, value(field(command.getClass(), "processInstanceId"), command));
        }
        return List.of();
    }

    private void rejectAutomationSignalBroadcast(String signal) {
        if (signal == null) return;
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_catalog.to_regclass(?) IS NOT NULL", Boolean.class,
                schema + ".tianji_process_instance_binding"))) return;
        var targets = jdbc.queryForList("SELECT execution_id_ FROM " + schema + ".act_ru_event_subscr"
                + " WHERE event_type_='signal' AND event_name_=? AND execution_id_ IS NOT NULL", String.class, signal);
        for (String execution : targets) {
            if (locate(new AddressVO(Kind.EXECUTION, execution)).isPresent()) {
                throw new IllegalArgumentException("Automation signals must target one root instance");
            }
        }
    }

    private static List<AddressVO> one(Kind kind, Object id) {
        return id instanceof String text ? List.of(new AddressVO(kind, text)) : List.of();
    }

    private static List<AddressVO> multiple(Kind kind, Object ids) {
        if (!(ids instanceof Collection<?> children)) return List.of();
        return children.stream().filter(String.class::isInstance).map(String.class::cast).distinct().map(id -> new AddressVO(kind,id)).toList();
    }

    private static boolean valid(String id) {
        return id != null && !id.isBlank() && id.length() <= 255;
    }

    private static Field field(Class<?> type, String name) {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
            try {
                var field = owner.getDeclaredField(name);
                if (!field.trySetAccessible()) throw unavailable();
                return field;
            } catch (NoSuchFieldException seekParent) {
                // The pinned Flowable ABI declares some command targets on a shared base type.
            }
        }
        throw unavailable();
    }

    private static Object value(Field field, Object source) {
        try {
            return field.get(source);
        } catch (IllegalAccessException invariant) {
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Automation root guard is unavailable or inconsistent");
    }

    private enum Kind { EXECUTION, TASK, JOB }
    private record AddressVO(Kind kind, String id) { }
    private record NativeRootVO(String instanceId, String tenant, boolean hasBinding, boolean hasRelease,
                                String definitionTenant, boolean ambiguousInputs, String definitionId, String definitionKey) { }
    private record RootVO(long tenantId, String instanceId) { }

    private record RootMarker(RootVO root) implements TransactionSynchronization {
        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResourceIfPossible(TX_MARKER);
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(TX_MARKER, root);
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(TX_MARKER);
        }
    }
}
