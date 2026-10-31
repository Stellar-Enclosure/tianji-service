package top.egon.cola.platform.tianji.agent.server.security;

import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.contract.memory.MemoryClient;
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
@PermissionMessage(pageCode = "tianji.permissions", key = "api.agent.contextSnapshots", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read prepared Agent context metadata"))
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
@PermissionDefinition(code = TianjiAgentPermissions.MEMORY_READ, meaning = "Read current authorized Memory facts",
        labelPage = "tianji.permissions", labelKey = "permission.memory.read")
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.memory.read", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read Memory"))
@PermissionDefinition(code = TianjiAgentPermissions.MEMORY_WRITE, meaning = "Create and correct explicit authorized Memory facts",
        labelPage = "tianji.permissions", labelKey = "permission.memory.write")
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.memory.write", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Create and correct Memory"))
@PermissionDefinition(code = TianjiAgentPermissions.MEMORY_FORGET, meaning = "Forget authorized Memory facts and redact historical content",
        labelPage = "tianji.permissions", labelKey = "permission.memory.forget")
@PermissionMessage(pageCode = "tianji.permissions", key = "permission.memory.forget", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Forget Memory"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.get", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read an authorized Memory fact"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.retrieve", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Retrieve authorized Memory facts"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.candidates", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Review unconfirmed Memory candidates"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.extractPreferences", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Extract candidates from approved owned preference sources"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.preparePreferences", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Prepare an approved owned preference source"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.mergeSuggestions", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Compare an unconfirmed Memory candidate with active facts"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.preferenceIntentStatus", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Read owned preference extraction progress"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.cancelPreferenceIntent", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Cancel pending owned preference extraction"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.upsert", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Create or deduplicate a Memory fact"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.correct", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Confirm or correct a Memory fact"))
@PermissionMessage(pageCode = "tianji.permissions", key = "api.memory.forget", defaultLocale = "en-US",
        defaults = @LocalizedText(locale = "en-US", text = "Forget a Memory fact"))
public final class TianjiAgentPermissions {
    public static final String MEMORY_READ = "TIANJI_MEMORY_READ";
    public static final String MEMORY_READ_SCOPE = MemoryClient.READ_SCOPE;
    public static final String MEMORY_WRITE = "TIANJI_MEMORY_WRITE";
    public static final String MEMORY_WRITE_SCOPE = MemoryClient.WRITE_SCOPE;
    public static final String MEMORY_FORGET = "TIANJI_MEMORY_FORGET";
    public static final String MEMORY_FORGET_SCOPE = MemoryClient.FORGET_SCOPE;
    public static final String MEMORY_GET_API = "tianji.api.memory.get";
    public static final String MEMORY_RETRIEVE_API = "tianji.api.memory.retrieve";
    public static final String MEMORY_CANDIDATES_API = "tianji.api.memory.candidates";
    public static final String MEMORY_EXTRACT_PREFERENCES_API = "tianji.api.memory.extractPreferences";
    public static final String MEMORY_PREPARE_PREFERENCES_API = "tianji.api.memory.preparePreferences";
    public static final String MEMORY_MERGE_SUGGESTIONS_API = "tianji.api.memory.mergeSuggestions";
    public static final String MEMORY_PREFERENCE_INTENT_STATUS_API = "tianji.api.memory.preferenceIntentStatus";
    public static final String MEMORY_PREFERENCE_INTENT_CANCEL_API = "tianji.api.memory.cancelPreferenceIntent";
    public static final String MEMORY_UPSERT_API = "tianji.api.memory.upsert";
    public static final String MEMORY_CORRECT_API = "tianji.api.memory.correct";
    public static final String MEMORY_FORGET_API = "tianji.api.memory.forget";

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
    public static final String CONTEXT_SNAPSHOTS_API = "tianji.api.agent.contextSnapshots";
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
