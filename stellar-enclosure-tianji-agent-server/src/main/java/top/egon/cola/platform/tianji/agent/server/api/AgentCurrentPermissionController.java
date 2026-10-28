package top.egon.cola.platform.tianji.agent.server.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.component.common.core.pojo.ResultRecord;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.CurrentEffectivePermissionView;

import java.util.Objects;

/** Exposes the authenticated user's current, deployment-bound permission view for the console. */
@RestController
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public class AgentCurrentPermissionController {
    private final CurrentEffectivePermissionView permissions;

    public AgentCurrentPermissionController(CurrentEffectivePermissionView permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @PermissionResource(key = TianjiAgentPermissions.CURRENT_AUTH_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.authorization.current", access = ResourceAccess.PUBLIC,
            allowedPrincipals = PermissionManifest.PrincipalType.USER)
    @GetMapping(path = "/api/v1/authorization/effective-permissions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResultRecord<EffectivePermissionSnapshot>> current(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResultRecord.success(permissions.current(request, TianjiAgentPermissions.CURRENT_AUTH_API)));
    }
}
