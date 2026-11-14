package top.egon.cola.platform.tianji.process.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskResult;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

@Slf4j
@Validated
@RestController("workerController")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "worker.enabled"}, havingValue = "true")
public class WorkerController {
    private static final String LEGACY_PATH = "/api/tianji/v1/worker-tasks";
    private static final String AUTOMATION_PATH = "/api/tianji/v2/worker-tasks";
    public static final String ACQUIRE_AUTOMATION_PATH = AUTOMATION_PATH + "/acquire";
    public static final String COMPLETE_AUTOMATION_PATH = AUTOMATION_PATH + "/complete";
    public static final String FAIL_AUTOMATION_PATH = AUTOMATION_PATH + "/fail";

    @Qualifier("flowableWorkerService") private final FlowableWorkerService workers;
    @Qualifier("tianjiProcessAccess") private final TianjiProcessAccess access;

    @PermissionResource(key = TianjiProcessPermissions.WORKER_ACQUIRE_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.acquire",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_ACQUIRE_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_ACQUIRE)
    @PostMapping(path = LEGACY_PATH + "/acquire", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AcquireWorkerTaskResult> acquire(@RequestBody AcquireWorkerTaskCommand command) {
        return noStore(workers.acquire(worker(TianjiProcessPermissions.WORKER_ACQUIRE_API), command));
    }

    @PermissionResource(key = TianjiProcessPermissions.WORKER_COMPLETE_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.complete",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_REPORT_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_REPORT)
    @PostMapping(path = LEGACY_PATH + "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkerReceiptDTO> complete(@RequestBody CompleteWorkerTaskCommand command) {
        return noStore(workers.complete(worker(TianjiProcessPermissions.WORKER_COMPLETE_API), command));
    }

    @PermissionResource(key = TianjiProcessPermissions.WORKER_FAIL_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.fail",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_REPORT_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_REPORT)
    @PostMapping(path = LEGACY_PATH + "/fail", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkerReceiptDTO> fail(@RequestBody FailWorkerTaskCommand command) {
        return noStore(workers.fail(worker(TianjiProcessPermissions.WORKER_FAIL_API), command));
    }

    @PermissionResource(key = TianjiProcessPermissions.AUTOMATION_WORKER_ACQUIRE_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.acquire",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_ACQUIRE_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_ACQUIRE)
    @PostMapping(path = ACQUIRE_AUTOMATION_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutomationWorkerAcquireDTO> acquireAutomationWorkerTask(
            @Valid @RequestBody AutomationWorkerAcquireCommand command, HttpServletRequest request) {
        requireNoQuery(request);
        return noStore(workers.acquireAutomation(worker(TianjiProcessPermissions.AUTOMATION_WORKER_ACQUIRE_API), command));
    }

    @PermissionResource(key = TianjiProcessPermissions.AUTOMATION_WORKER_COMPLETE_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.complete",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_REPORT_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_REPORT)
    @PostMapping(path = COMPLETE_AUTOMATION_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutomationWorkerReportDTO> completeAutomationWorkerTask(
            @Valid @RequestBody AutomationWorkerCompletionCommand command, HttpServletRequest request) {
        requireNoQuery(request);
        return noStore(workers.completeAutomation(worker(TianjiProcessPermissions.AUTOMATION_WORKER_COMPLETE_API), command));
    }

    @PermissionResource(key = TianjiProcessPermissions.AUTOMATION_WORKER_FAIL_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.worker.fail",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.WORKER_REPORT_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.WORKER_REPORT)
    @PostMapping(path = FAIL_AUTOMATION_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutomationWorkerReportDTO> failAutomationWorkerTask(
            @Valid @RequestBody AutomationWorkerFailureCommand command, HttpServletRequest request) {
        requireNoQuery(request);
        return noStore(workers.failAutomation(worker(TianjiProcessPermissions.AUTOMATION_WORKER_FAIL_API), command));
    }

    private static void requireNoQuery(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw new IllegalArgumentException("Invalid Worker request");
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
