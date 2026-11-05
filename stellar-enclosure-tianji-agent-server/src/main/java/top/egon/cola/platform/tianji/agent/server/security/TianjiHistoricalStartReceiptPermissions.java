package top.egon.cola.platform.tianji.agent.server.security;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionDefinition;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Independent historical write permission and exact servlet resource. */
@PermissionDefinition(code = TianjiHistoricalStartReceiptPermissions.RECONCILE,
        meaning = "Append verified historical START receipt evidence for an owned Agent run",
        labelPage = "tianji.permissions", labelKey = "permission.agent.toolReceiptReconcile")
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.agent.toolReceiptReconcile", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Reconcile an owned historical Agent START receipt"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.toolReceiptReconcile", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Record verified historical START receipt evidence"))
public final class TianjiHistoricalStartReceiptPermissions {
    public static final String RECONCILE = "TIANJI_AGENT_TOOL_RECEIPT_RECONCILE";
    public static final String SCOPE = "tianji:agent:tool-receipt:reconcile";
    public static final String RESOURCE = "tianji.api.agent.toolReceipt.reconcile";
    public static final String PATH = "/api/tianji/v1/agent-checkpoints/reconcile-start-receipt";
    public static final String ENABLED = "stellar.tianji.agent.recovery.historical-start-receipt.enabled";

    private TianjiHistoricalStartReceiptPermissions() { }
}
