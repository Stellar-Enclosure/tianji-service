package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.application.session.AgentSessionManager;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionListDTO;
import top.egon.cola.platform.tianji.contract.agent.CreateAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.DeleteAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentSessionQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/tianji/v1/agent-sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public class AgentSessionController {
    private final AgentSessionManager sessions;
    private final TianjiAgentAccess access;

    public AgentSessionController(AgentSessionManager sessions, TianjiAgentAccess access) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiAgentPermissions.SESSION_CREATE_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.session.create",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = {TianjiAgentPermissions.START_SCOPE, TianjiAgentPermissions.READ_SCOPE})
    @RequiresPermissions(codes = {TianjiAgentPermissions.START, TianjiAgentPermissions.READ})
    @PutMapping
    public ResponseEntity<AgentSessionDTO> create(@RequestParam String sessionId) {
        var actor = access.require(TianjiAgentPermissions.SESSION_CREATE_API).actor();
        return noStore(sessions.create(actor, new CreateAgentSessionCommand(sessionId)));
    }

    @PermissionResource(key = TianjiAgentPermissions.SESSION_GET_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.session.get",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.READ)
    @GetMapping("/detail")
    public ResponseEntity<AgentSessionDTO> get(@RequestParam String sessionId) {
        var actor = access.require(TianjiAgentPermissions.SESSION_GET_API).actor();
        return noStore(sessions.get(actor, new GetAgentSessionQuery(sessionId)));
    }

    @PermissionResource(key = TianjiAgentPermissions.SESSION_LIST_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.session.list",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.READ)
    @GetMapping
    public ResponseEntity<AgentSessionListDTO> list() {
        return noStore(sessions.list(access.require(TianjiAgentPermissions.SESSION_LIST_API).actor()));
    }

    @PermissionResource(key = TianjiAgentPermissions.SESSION_DELETE_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.session.delete",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = {TianjiAgentPermissions.READ_SCOPE, TianjiAgentPermissions.SESSION_DELETE_SCOPE})
    @RequiresPermissions(codes = {TianjiAgentPermissions.READ, TianjiAgentPermissions.SESSION_DELETE})
    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam String sessionId) {
        var actor = access.require(TianjiAgentPermissions.SESSION_DELETE_API).actor();
        sessions.delete(actor, new DeleteAgentSessionCommand(sessionId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
