package top.egon.cola.platform.tianji.agent.server.security;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.LocalizedText;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionDefinition;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionMessage;

/** Resource identities and permission meanings owned by the Tianji Agent server. */
@PermissionDefinition(code = TianjiAgentPermissions.START, meaning = "Start a tenant-scoped Agent run",
        labelPage = "tianji.permissions", labelKey = "permission.agent.start")
@PermissionDefinition(code = TianjiAgentPermissions.READ, meaning = "Read owned Agent runs and events",
        labelPage = "tianji.permissions", labelKey = "permission.agent.read")
@PermissionDefinition(code = TianjiAgentPermissions.CANCEL, meaning = "Request cancellation of an owned Agent run",
        labelPage = "tianji.permissions", labelKey = "permission.agent.cancel")
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.agent.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start Agent runs"))
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.agent.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read Agent runs and events"))
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.agent.cancel", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Cancel Agent runs"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start an Agent run"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an Agent run"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.list", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "List owned Agent runs"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.result", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an Agent result"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.events", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Observe Agent events"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.cancel", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Cancel an Agent run"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.authorization.current", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read the current permission view"))
public final class TianjiAgentPermissions {
    public static final String START = "TIANJI_AGENT_START";
    public static final String READ = "TIANJI_AGENT_READ";
    public static final String CANCEL = "TIANJI_AGENT_CANCEL";
    public static final String START_API = "tianji.api.agent.start";
    public static final String GET_API = "tianji.api.agent.get";
    public static final String LIST_API = "tianji.api.agent.list";
    public static final String RESULT_API = "tianji.api.agent.result";
    public static final String EVENTS_API = "tianji.api.agent.events";
    public static final String CANCEL_API = "tianji.api.agent.cancel";
    public static final String CURRENT_AUTH_API = "tianji.api.authorization.current";
    public static final String START_SCOPE = "tianji:agent:start";
    public static final String READ_SCOPE = "tianji:agent:read";
    public static final String CANCEL_SCOPE = "tianji:agent:cancel";

    private TianjiAgentPermissions() { }
}
