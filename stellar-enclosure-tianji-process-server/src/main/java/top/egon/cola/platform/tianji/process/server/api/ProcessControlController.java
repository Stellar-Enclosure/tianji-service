package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlSnapshotDTO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

/** Private owner control and cancellation operations. */
@RestController
@RequestMapping(path = "/api/tianji/v1/process-instances", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "cancellation.enabled"}, havingValue = "true")
public class ProcessControlController {
    private final ProcessControlService controls;
    private final TianjiProcessAccess access;

    public ProcessControlController(ProcessControlService controls, TianjiProcessAccess access) {
        this.controls = Objects.requireNonNull(controls, "controls");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiProcessPermissions.CONTROL_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.process.control",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = {TianjiProcessPermissions.READ_SCOPE, TianjiProcessPermissions.CANCEL_SCOPE})
    @RequiresPermissions(codes = {TianjiProcessPermissions.READ, TianjiProcessPermissions.CANCEL})
    @GetMapping("/{instanceId}/control")
    public ResponseEntity<ProcessControlSnapshotDTO> control(@PathVariable("instanceId") String instanceId) {
        var actor = access.require(TianjiProcessPermissions.CONTROL_API);
        return noStore(controls.control(actor, new GetProcessInstanceQuery(instanceId)));
    }

    @PermissionResource(key = TianjiProcessPermissions.CANCEL_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.process.cancel",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = {TianjiProcessPermissions.READ_SCOPE, TianjiProcessPermissions.CANCEL_SCOPE})
    @RequiresPermissions(codes = {TianjiProcessPermissions.READ, TianjiProcessPermissions.CANCEL})
    @PostMapping(path = "/{instanceId}/cancellations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancelProcessResult> cancel(@PathVariable("instanceId") String instanceId,
                                                       @RequestBody CancelProcessCommand command) {
        var actor = access.require(TianjiProcessPermissions.CANCEL_API);
        if (!instanceId.equals(command.instanceId())) throw new IllegalArgumentException("Process cancellation target is invalid");
        return noStore(controls.cancel(actor, command));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(body);
    }
}
