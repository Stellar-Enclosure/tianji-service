package top.egon.cola.platform.tianji.agent.server.security;

import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
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
@PermissionDefinition(code = TianjiAgentPermissions.SESSION_DELETE, meaning = "Delete an owned Agent session",
        labelPage = "tianji.permissions", labelKey = "permission.session.delete")
@PermissionDefinition(code = TianjiAgentPermissions.LINKED_SUBMIT, meaning = "Submit an authorized process-linked Agent run",
        labelPage = "tianji.permissions", labelKey = "permission.linked.submit")
@PermissionDefinition(code = TianjiAgentPermissions.LINKED_RESOLVE, meaning = "Resolve an owned process-linked Agent submission",
        labelPage = "tianji.permissions", labelKey = "permission.linked.resolve")
@PermissionDefinition(code = TianjiAgentPermissions.LINKED_CLOSE, meaning = "Close an owned process-linked Agent submission",
        labelPage = "tianji.permissions", labelKey = "permission.linked.close")
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.agent.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start Agent runs"))
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.agent.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read Agent runs and events"))
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.agent.cancel", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Cancel Agent runs"))
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.session.delete", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Delete Agent sessions"))
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.linked.submit", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Submit linked Agent runs"))
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.linked.resolve", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Resolve linked Agent submissions"))
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.linked.close", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Close linked Agent submissions"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.start", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start an Agent run"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.startSession", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Start an Agent run with a session"))
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
@PermissionMessage(pageCode = "tianji.permissions", key = "api.session.create", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Create an Agent session"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.session.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an Agent session"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.session.list", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "List owned Agent sessions"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.session.delete", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Delete an Agent session"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.linked.submit", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Submit a linked Agent run"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.linked.resolve", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Resolve a linked Agent submission"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.linked.close", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Close a linked Agent submission"))
public final class TianjiAgentPermissions {
    public static final String START = "TIANJI_AGENT_START";
    public static final String READ = "TIANJI_AGENT_READ";
    public static final String CANCEL = "TIANJI_AGENT_CANCEL";
    public static final String SESSION_DELETE = "TIANJI_SESSION_DELETE";
    public static final String LINKED_SUBMIT = "TIANJI_LINKED_AGENT_SUBMIT";
    public static final String LINKED_RESOLVE = "TIANJI_LINKED_AGENT_RESOLVE";
    public static final String LINKED_CLOSE = "TIANJI_LINKED_AGENT_CLOSE";
    public static final String START_API = "tianji.api.agent.start";
    public static final String SESSION_START_API = "tianji.api.agent.startSession";
    public static final String GET_API = "tianji.api.agent.get";
    public static final String LIST_API = "tianji.api.agent.list";
    public static final String RESULT_API = "tianji.api.agent.result";
    public static final String EVENTS_API = "tianji.api.agent.events";
    public static final String CANCEL_API = "tianji.api.agent.cancel";
    public static final String CURRENT_AUTH_API = "tianji.api.authorization.current";
    public static final String SESSION_CREATE_API = "tianji.api.session.create";
    public static final String SESSION_GET_API = "tianji.api.session.get";
    public static final String SESSION_LIST_API = "tianji.api.session.list";
    public static final String SESSION_DELETE_API = "tianji.api.session.delete";
    public static final String LINKED_SUBMIT_API = "tianji.api.agent.linkedSubmit";
    public static final String LINKED_RESOLVE_API = "tianji.api.agent.linkedResolve";
    public static final String LINKED_CLOSE_API = "tianji.api.agent.linkedClose";
    public static final String START_SCOPE = "tianji:agent:start";
    public static final String READ_SCOPE = "tianji:agent:read";
    public static final String CANCEL_SCOPE = "tianji:agent:cancel";
    public static final String SESSION_DELETE_SCOPE = "tianji:session:delete";
    public static final String LINKED_SUBMIT_SCOPE = LinkedAgentClient.SUBMIT_SCOPE;
    public static final String LINKED_RESOLVE_SCOPE = LinkedAgentClient.RESOLVE_SCOPE;
    public static final String LINKED_CLOSE_SCOPE = LinkedAgentClient.CLOSE_SCOPE;

    private TianjiAgentPermissions() { }
}
