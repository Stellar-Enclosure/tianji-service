package top.egon.cola.platform.tianji.process.server.security;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionDefinition;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

@PermissionDefinition(code = TianjiProcessPermissions.RELEASE_PUBLISH, meaning = "Publish a tenant-scoped process release",
        labelPage = "tianji.process.permissions", labelKey = "permission.release.publish")
@PermissionDefinition(code = TianjiProcessPermissions.RELEASE_READ, meaning = "Read a tenant-scoped process release",
        labelPage = "tianji.process.permissions", labelKey = "permission.release.read")
@PermissionDefinition(code = TianjiProcessPermissions.START, meaning = "Start a process from a fixed release",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.start")
@PermissionDefinition(code = TianjiProcessPermissions.READ, meaning = "Read an owned process instance",
        labelPage = "tianji.process.permissions", labelKey = "permission.process.read")
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
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start a process instance"))
@PermissionMessage(pageCode = "tianji.process.permissions", key = "api.process.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read a process instance"))
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
public final class TianjiProcessPermissions {
    public static final String RELEASE_PUBLISH = "TIANJI_PROCESS_RELEASE_PUBLISH";
    public static final String RELEASE_READ = "TIANJI_PROCESS_RELEASE_READ";
    public static final String START = "TIANJI_PROCESS_START";
    public static final String READ = "TIANJI_PROCESS_READ";
    public static final String DECISION_EXECUTE = "TIANJI_DECISION_EXECUTE";
    public static final String WORKER_ACQUIRE = "TIANJI_WORKER_ACQUIRE";
    public static final String WORKER_REPORT = "TIANJI_WORKER_REPORT";
    public static final String AGENT_RESULT_RECEIVE = "TIANJI_AGENT_RESULT_RECEIVE";
    public static final String RELEASE_PUBLISH_API = "tianji.api.process.release.publish";
    public static final String RELEASE_GET_API = "tianji.api.process.release.get";
    public static final String START_API = "tianji.api.process.instance.start";
    public static final String GET_API = "tianji.api.process.instance.get";
    public static final String DECISION_API = "tianji.api.process.decision.evaluate";
    public static final String WORKER_ACQUIRE_API = "tianji.api.process.worker.acquire";
    public static final String WORKER_COMPLETE_API = "tianji.api.process.worker.complete";
    public static final String WORKER_FAIL_API = "tianji.api.process.worker.fail";
    public static final String AGENT_RESULT_RECEIVE_API = "tianji.api.process.agent.result.receive";
    public static final String RELEASE_PUBLISH_SCOPE = "tianji:process:publish";
    public static final String RELEASE_READ_SCOPE = "tianji:process:release:read";
    public static final String START_SCOPE = "tianji:process:start";
    public static final String READ_SCOPE = "tianji:process:read";
    public static final String DECISION_SCOPE = "tianji:decision:execute";
    public static final String WORKER_ACQUIRE_SCOPE = "tianji:worker:acquire";
    public static final String WORKER_REPORT_SCOPE = "tianji:worker:report";
    public static final String AGENT_RESULT_RECEIVE_SCOPE = "tianji:process:agent-result:receive";

    private TianjiProcessPermissions() { }
}
