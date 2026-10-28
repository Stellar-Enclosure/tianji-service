package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskResult;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/tianji/v1/worker-tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "worker.enabled"}, havingValue = "true")
public class WorkerController {
    private final FlowableWorkerService workers;
    private final TianjiProcessAccess access;

    public WorkerController(FlowableWorkerService workers, TianjiProcessAccess access) {
        this.workers = Objects.requireNonNull(workers, "workers");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiProcessPermissions.WORKER_ACQUIRE_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.acquire",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_ACQUIRE_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_ACQUIRE)
    @PostMapping(path = "/acquire", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AcquireWorkerTaskResult> acquire(@RequestBody AcquireWorkerTaskCommand command) {
        return noStore(workers.acquire(worker(TianjiProcessPermissions.WORKER_ACQUIRE_API), command));
    }

    @PermissionResource(key = TianjiProcessPermissions.WORKER_COMPLETE_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.complete",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_REPORT_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_REPORT)
    @PostMapping(path = "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkerReceiptDTO> complete(@RequestBody CompleteWorkerTaskCommand command) {
        return noStore(workers.complete(worker(TianjiProcessPermissions.WORKER_COMPLETE_API), command));
    }

    @PermissionResource(key = TianjiProcessPermissions.WORKER_FAIL_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.fail",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_REPORT_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_REPORT)
    @PostMapping(path = "/fail", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkerReceiptDTO> fail(@RequestBody FailWorkerTaskCommand command) {
        return noStore(workers.fail(worker(TianjiProcessPermissions.WORKER_FAIL_API), command));
    }

    private ProcessActor worker(String resource) {
        ProcessActor actor = access.require(resource);
        if (actor.subjectType() != ProcessActor.SubjectType.SERVICE) throw new AccessDeniedException("service identity required");
        return actor;
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
