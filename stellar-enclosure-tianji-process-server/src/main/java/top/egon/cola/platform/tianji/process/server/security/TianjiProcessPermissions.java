package top.egon.cola.platform.tianji.process.server.security;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionDefinition;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

@PermissionDefinition(code = TianjiProcessPermissions.RELEASE_PUBLISH, meaning = "Publish a tenant-scoped process release",
        labelPage = "tianji.process.permissions", labelKey = "permission.release.publish")
@PermissionDefinition(code = TianjiProcessPermissions.RELEASE_READ, meaning = "Read a tenant-scoped process release",
        labelPage = "tianji.process.permissions", labelKey = "permission.release.read")
@PermissionDefinition(code = TianjiProcessPermissions.RELEASE_ACTIVATE, meaning = "Activate an immutable process release",
        labelPage = "tianji.process.permissions", labelKey = "permission.release.activate")
@PermissionDefinition(code = TianjiProcessPermissions.OBSERVE, meaning = "Observe automation instance metadata in the current tenant",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.observe")
@PermissionDefinition(code = TianjiProcessPermissions.DATA_READ, meaning = "Read specifically authorized automation business data",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.data.read")
@PermissionDefinition(code = TianjiProcessPermissions.DIAGNOSTIC_READ, meaning = "Read authorized automation diagnostic references",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.diagnostic.read")
@PermissionDefinition(code = TianjiProcessPermissions.START, meaning = "Start a process from a fixed release",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.start")
@PermissionDefinition(code = TianjiProcessPermissions.READ, meaning = "Read an owned process instance",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.read")
@PermissionDefinition(code = TianjiProcessPermissions.CANCEL, meaning = "Cancel an owned process instance",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.cancel")
@PermissionDefinition(code = TianjiProcessPermissions.NODE_RETRY, meaning = "Request recovery for an eligible automation node",
        labelPage = "tianji.process.permissions", labelKey = "permission.node.retry")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.node.retry", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Request recovery for an eligible automation node"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.node.retry", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Retry an eligible automation node"))
@PermissionDefinition(code = TianjiProcessPermissions.WORKER_RECOVER, meaning = "Recover an eligible Process Worker failure",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.workerRecover")
@PermissionDefinition(code = TianjiProcessPermissions.DECISION_EXECUTE, meaning = "Execute a decision from a fixed release",
        labelPage = "tianji.process.permissions", labelKey = "permission.decision.execute")
@PermissionDefinition(code = TianjiProcessPermissions.WORKER_ACQUIRE, meaning = "Acquire an authorized worker task",
        labelPage = "tianji.process.permissions", labelKey = "permission.worker.acquire")
@PermissionDefinition(code = TianjiProcessPermissions.WORKER_REPORT, meaning = "Report a worker task outcome",
        labelPage = "tianji.process.permissions", labelKey = "permission.worker.report")
@PermissionDefinition(code = TianjiProcessPermissions.AGENT_RESULT_RECEIVE, meaning = "Receive an authorized Agent terminal reference",
        labelPage = "tianji.process.permissions", labelKey = "permission.agent.result.receive")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.release.publish", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Publish process releases"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.release.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read process releases"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.process.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start process instances"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.process.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read owned process instances"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.process.cancel", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Cancel owned process instances"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.process.workerRecover", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Recover eligible Process Worker failures"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.decision.execute", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Execute decisions"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.worker.acquire", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Acquire worker tasks"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.worker.report", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Report worker task outcomes"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.release.publish", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Publish a process release"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.release.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read a process release"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.model.validate", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Validate an exact process draft revision"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.release.publish", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Publish an exact process draft revision"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.release.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an immutable automation release source"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.release.activate", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Activate a process release"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.process.observe", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Observe automation instances"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.process.data.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read authorized automation business data"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.process.diagnostic.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read automation diagnostic references"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.release.activate", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Activate an immutable automation release"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.activation.list", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read current and historical automation activations"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.instance.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start an instance from the active process release"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.instance.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an authorized automation instance"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.automation.node.execution.list", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "List executions in an authorized automation instance"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.engine.task.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an authorized automation action task"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.engine.task.complete", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Complete an assigned automation action task"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.draft.create", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Create an owned process draft"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.draft.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an owned process draft"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.draft.save", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Save an owned process draft"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.draft.list", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "List owned process drafts"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.draft.discard", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Discard an owned process draft"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start a process instance"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.startReceipt", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an owned Process START receipt"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read a process instance"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.agent.outcomes", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read owned process Agent outcomes"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.migrationPreflight", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an owned BPMN migration preflight"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.correlations", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Find owned process starts by correlation"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.control", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Review process control"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.cancel", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Cancel a process instance"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.workerFailures", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Observe Process Worker failures"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.workerRecoveries", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Schedule Process Worker recovery"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.decision.evaluate", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Evaluate a decision"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.worker.acquire", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Acquire a worker task"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.worker.complete", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Complete a worker task"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.worker.fail", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Report a worker task failure"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.agent.result.receive", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Receive Agent results"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.agent.result.receive", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Receive an Agent result reference"))
@PermissionDefinition(code = TianjiProcessPermissions.TASK_READ, meaning = "Read governed human tasks",
        labelPage = "tianji.process.permissions", labelKey = "permission.task.read")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.task.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read human tasks"))
@PermissionDefinition(code = TianjiProcessPermissions.TASK_CLAIM, meaning = "Claim and release governed human tasks",
        labelPage = "tianji.process.permissions", labelKey = "permission.task.claim")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.task.claim", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Claim and release human tasks"))
@PermissionDefinition(code = TianjiProcessPermissions.TASK_COMPLETE, meaning = "Complete governed human tasks",
        labelPage = "tianji.process.permissions", labelKey = "permission.task.complete")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.task.complete", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Complete human tasks"))
@PermissionDefinition(code = TianjiProcessPermissions.TASK_TRANSFER, meaning = "Administratively transfer governed human tasks",
        labelPage = "tianji.process.permissions", labelKey = "permission.task.transfer")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.task.transfer", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Administratively transfer human tasks"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.task.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read a human task"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.task.query", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Query current human tasks"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.task.adminQuery", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Administratively query human tasks"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.task.claim", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Claim a human task"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.task.unclaim", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Release a human task claim"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.task.transfer", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Transfer a human task queue"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.task.complete", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Complete a human task"))
@PermissionDefinition(code = TianjiProcessPermissions.APPROVAL_VERIFY, meaning = "Verify pinned approval provenance for a registered service consumer",
        labelPage = "tianji.process.permissions", labelKey = "permission.task.approval.verify")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.task.approval.verify", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Verify approval provenance"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.task.approval.verify", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Verify an approval reference"))
@PermissionDefinition(code = TianjiProcessPermissions.CASE_START, meaning = "Start a Case from a fixed release",
        labelPage = "tianji.process.permissions", labelKey = "permission.case.start")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.case.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start a Case from a fixed release"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.case.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start a Case from a fixed release"))
@PermissionDefinition(code = TianjiProcessPermissions.CASE_READ, meaning = "Read an authorized Case",
        labelPage = "tianji.process.permissions", labelKey = "permission.case.read")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.case.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an authorized Case"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.case.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an authorized Case"))
@PermissionDefinition(code = TianjiProcessPermissions.CASE_PLAN_ITEM_ACT, meaning = "Act on a governed Case plan item",
        labelPage = "tianji.process.permissions", labelKey = "permission.case.act")
@PermissionMessage(pageCode = "tianji.process.permissions", key = "permission.case.act", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Act on a governed Case plan item"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.case.actPlanItem", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Act on a governed Case plan item"))
public final class TianjiProcessPermissions {
    public static final String CASE_START = "TIANJI_CASE_START";
    public static final String CASE_READ = "TIANJI_CASE_READ";
    public static final String CASE_PLAN_ITEM_ACT = "TIANJI_CASE_PLAN_ITEM_ACT";
    public static final String CASE_START_API = "tianji.api.process.case.start";
    public static final String CASE_GET_API = "tianji.api.process.case.get";
    public static final String CASE_PLAN_ITEM_ACT_API = "tianji.api.process.case.actPlanItem";
    public static final String CASE_START_SCOPE = "tianji:case:start";
    public static final String CASE_READ_SCOPE = "tianji:case:read";
    public static final String CASE_PLAN_ITEM_ACT_SCOPE = "tianji:case:act";
    public static final String APPROVAL_VERIFY = "TIANJI_TASK_APPROVAL_VERIFY";
    public static final String APPROVAL_VERIFY_API = "tianji.api.process.task.approval.verify";
    public static final String TASK_READ = "TIANJI_TASK_READ";
    public static final String TASK_CLAIM = "TIANJI_TASK_CLAIM";
    public static final String TASK_COMPLETE = "TIANJI_TASK_COMPLETE";
    public static final String TASK_TRANSFER = "TIANJI_TASK_TRANSFER";
    public static final String TASK_GET_API = "tianji.api.process.task.get";
    public static final String TASK_QUERY_API = "tianji.api.process.task.query";
    public static final String TASK_ADMIN_QUERY_API = "tianji.api.process.task.adminQuery";
    public static final String TASK_CLAIM_API = "tianji.api.process.task.claim";
    public static final String TASK_UNCLAIM_API = "tianji.api.process.task.unclaim";
    public static final String TASK_TRANSFER_API = "tianji.api.process.task.transfer";
    public static final String TASK_COMPLETE_API = "tianji.api.process.task.complete";
    public static final String RELEASE_PUBLISH = "TIANJI_PROCESS_RELEASE_PUBLISH";
    public static final String RELEASE_READ = "TIANJI_PROCESS_RELEASE_READ";
    public static final String RELEASE_ACTIVATE = "TIANJI_PROCESS_RELEASE_ACTIVATE";
    public static final String START = "TIANJI_PROCESS_START";
    public static final String READ = "TIANJI_PROCESS_READ";
    public static final String OBSERVE = "TIANJI_PROCESS_OBSERVE";
    public static final String DATA_READ = "TIANJI_PROCESS_DATA_READ";
    public static final String DIAGNOSTIC_READ = "TIANJI_PROCESS_DIAGNOSTIC_READ";
    public static final String CANCEL = "TIANJI_PROCESS_CANCEL";
    public static final String WORKER_RECOVER = "TIANJI_PROCESS_WORKER_RECOVER";
    public static final String DECISION_EXECUTE = "TIANJI_DECISION_EXECUTE";
    public static final String WORKER_ACQUIRE = "TIANJI_WORKER_ACQUIRE";
    public static final String WORKER_REPORT = "TIANJI_WORKER_REPORT";
    public static final String AGENT_RESULT_RECEIVE = "TIANJI_AGENT_RESULT_RECEIVE";
    public static final String RELEASE_PUBLISH_API = "tianji.api.process.release.publish";
    public static final String AUTOMATION_MODEL_VALIDATE_API = "tianji.api.process.model.validate.v2";
    public static final String AUTOMATION_RELEASE_PUBLISH_API = "tianji.api.process.release.publish.v2";
    public static final String AUTOMATION_RELEASE_GET_API = "tianji.api.process.release.get.v2";
    public static final String AUTOMATION_RELEASE_ACTIVATE_API = "tianji.api.process.release.activate.v2";
    public static final String AUTOMATION_ACTIVATION_LIST_API = "tianji.api.process.activation.list.v2";
    public static final String AUTOMATION_INSTANCE_START_API = "tianji.api.process.instance.start.v2";
    public static final String AUTOMATION_INSTANCE_GET_API = "tianji.api.process.instance.get.v2";
    public static final String AUTOMATION_NODE_RETRY_API = "tianji.api.process.node.retry.v2";
    public static final String AUTOMATION_NODE_EXECUTION_LIST_API = "tianji.api.process.node.execution.list.v2";
    public static final String ENGINE_TASK_GET_API = "tianji.api.process.task.get.v2";
    public static final String ENGINE_TASK_COMPLETE_API = "tianji.api.process.task.complete.v2";
    public static final String DRAFT_CREATE_API = "tianji.api.process.draft.create.v2";
    public static final String DRAFT_GET_API = "tianji.api.process.draft.get.v2";
    public static final String DRAFT_SAVE_API = "tianji.api.process.draft.save.v2";
    public static final String DRAFT_LIST_API = "tianji.api.process.draft.list.v2";
    public static final String DRAFT_DISCARD_API = "tianji.api.process.draft.discard.v2";
    public static final String RELEASE_GET_API = top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseClient.READ_RESOURCE_KEY;
    public static final String START_API = top.egon.cola.platform.tianji.contract.orchestration.ProcessClient.START_RESOURCE_KEY;
    public static final String MIGRATION_PREFLIGHT_API = "tianji.api.process.migration.preflight";
    public static final String AGENT_OUTCOMES_API = "tianji.api.process.agent.outcomes";
    public static final String CORRELATION_LIST_API = "tianji.api.process.instance.correlations";
    public static final String GET_API = "tianji.api.process.instance.get";
    public static final String START_RECEIPT_GET_API = "tianji.api.process.startReceipt.get";
    public static final String CONTROL_API = top.egon.cola.platform.tianji.contract.orchestration.ProcessControlClient.CONTROL_RESOURCE_KEY;
    public static final String CANCEL_API = top.egon.cola.platform.tianji.contract.orchestration.ProcessControlClient.CANCEL_RESOURCE_KEY;
    public static final String WORKER_FAILURES_API = top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient.WORKER_FAILURES_RESOURCE_KEY;
    public static final String WORKER_RECOVERIES_API = top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient.WORKER_RECOVERIES_RESOURCE_KEY;
    public static final String DECISION_API = top.egon.cola.platform.tianji.contract.orchestration.DecisionClient.EXECUTE_RESOURCE_KEY;
    public static final String WORKER_ACQUIRE_API = "tianji.api.process.worker.acquire";
    public static final String WORKER_COMPLETE_API = "tianji.api.process.worker.complete";
    public static final String WORKER_FAIL_API = "tianji.api.process.worker.fail";
    public static final String AUTOMATION_WORKER_ACQUIRE_API = "tianji.api.process.worker.acquire.v2";
    public static final String AUTOMATION_WORKER_COMPLETE_API = "tianji.api.process.worker.complete.v2";
    public static final String AUTOMATION_WORKER_FAIL_API = "tianji.api.process.worker.fail.v2";
    public static final String AGENT_RESULT_RECEIVE_API = "tianji.api.process.agent.result.receive";
    public static final String RELEASE_PUBLISH_SCOPE = "tianji:process:publish";
    public static final String RELEASE_READ_SCOPE = top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseClient.READ_SCOPE;
    public static final String RELEASE_ACTIVATE_SCOPE = "tianji:process:activate";
    public static final String START_SCOPE = top.egon.cola.platform.tianji.contract.orchestration.ProcessClient.START_SCOPE;
    public static final String READ_SCOPE = "tianji:process:read";
    public static final String NODE_RETRY = "TIANJI_PROCESS_NODE_RETRY";
    public static final String NODE_RETRY_SCOPE = "tianji:process:node:retry";
    public static final String OBSERVE_SCOPE = "tianji:process:observe";
    public static final String DATA_READ_SCOPE = "tianji:process:data:read";
    public static final String DIAGNOSTIC_READ_SCOPE = "tianji:process:diagnostic:read";
    public static final String CANCEL_SCOPE = top.egon.cola.platform.tianji.contract.orchestration.ProcessControlClient.CANCEL_SCOPE;
    public static final String WORKER_RECOVER_SCOPE = top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient.WORKER_RECOVER_SCOPE;
    public static final String DECISION_SCOPE = top.egon.cola.platform.tianji.contract.orchestration.DecisionClient.EXECUTE_SCOPE;
    public static final String WORKER_ACQUIRE_SCOPE = "tianji:worker:acquire";
    public static final String WORKER_REPORT_SCOPE = "tianji:worker:report";
    public static final String AGENT_RESULT_RECEIVE_SCOPE = top.egon.cola.platform.tianji.contract.orchestration.AgentResultClient.RECEIVE_SCOPE;

    private TianjiProcessPermissions() { }
}
