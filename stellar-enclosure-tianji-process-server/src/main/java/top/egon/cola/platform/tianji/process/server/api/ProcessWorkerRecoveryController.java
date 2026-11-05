package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailuresDTO;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;
import top.egon.cola.platform.tianji.process.server.execution.ProcessWorkerRecoveryService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

/** Private owner observation and Worker recovery operations. */
@RestController
@RequestMapping(path = "/api/tianji/v1/process-instances", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process",
        name = {"api.enabled", "worker.enabled", "worker-recovery.enabled"}, havingValue = "true")
public class ProcessWorkerRecoveryController {
    private final ProcessWorkerRecoveryService service;
    private final TianjiProcessAccess access;

    public ProcessWorkerRecoveryController(@Qualifier("processWorkerRecoveryService") ProcessWorkerRecoveryService service,
                                           TianjiProcessAccess access) {
        this.service = Objects.requireNonNull(service, "service");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiProcessPermissions.WORKER_FAILURES_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.process.workerFailures",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = {TianjiProcessPermissions.READ_SCOPE, TianjiProcessPermissions.WORKER_RECOVER_SCOPE})
    @RequiresPermissions(codes = {TianjiProcessPermissions.READ, TianjiProcessPermissions.WORKER_RECOVER})
    @GetMapping("/{instanceId}/worker-failures")
    public ResponseEntity<ProcessWorkerFailuresDTO> failures(@PathVariable("instanceId") String instanceId) {
        var actor = access.require(TianjiProcessPermissions.WORKER_FAILURES_API);
        return noStore(service.workerFailures(actor, new GetProcessInstanceQuery(instanceId)));
    }

    @PermissionResource(key = TianjiProcessPermissions.WORKER_RECOVERIES_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.process.workerRecoveries",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = {TianjiProcessPermissions.READ_SCOPE, TianjiProcessPermissions.WORKER_RECOVER_SCOPE})
    @RequiresPermissions(codes = {TianjiProcessPermissions.READ, TianjiProcessPermissions.WORKER_RECOVER})
    @PostMapping(path = "/{instanceId}/worker-recoveries", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecoverProcessWorkerResult> recover(@PathVariable("instanceId") String instanceId,
                                                              @RequestBody RecoverProcessWorkerCommand command) {
        var actor = access.require(TianjiProcessPermissions.WORKER_RECOVERIES_API);
        if (!instanceId.equals(command.instanceId())) throw new IllegalArgumentException("Process Worker recovery target is invalid");
        return noStore(service.recoverWorker(actor, command));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(body);
    }
}
