package top.egon.cola.platform.tianji.process.server.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskClient;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.TransferHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.security.TianjiHumanTaskAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Private current-user task operations. Neither request bodies nor route parameters establish identity or role membership. */
@RestController
@RequestMapping(path = HumanTaskClient.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "human-tasks.enabled"}, havingValue = "true")
public class HumanTaskController {
    private static final Logger log = LoggerFactory.getLogger(HumanTaskController.class);
    private final FlowableHumanTaskService tasks;
    private final TianjiHumanTaskAccess access;
    public HumanTaskController(FlowableHumanTaskService tasks, TianjiHumanTaskAccess access) { this.tasks = Objects.requireNonNull(tasks, "tasks"); this.access = Objects.requireNonNull(access, "access"); }

    @PermissionResource(key = TianjiProcessPermissions.TASK_GET_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.task.get",
            allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = HumanTaskClient.READ_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.TASK_READ)
    @PostMapping(path = "/get", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HumanTaskDTO> get(@RequestBody GetHumanTaskQuery query) { return execute(TianjiProcessPermissions.TASK_GET_API, query.taskId(), null, context -> tasks.get(context, query)); }

    @PermissionResource(key = TianjiProcessPermissions.TASK_QUERY_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.task.query",
            allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = HumanTaskClient.READ_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.TASK_READ)
    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HumanTaskPageDTO> query(@RequestBody ListHumanTasksQuery query) { return execute(TianjiProcessPermissions.TASK_QUERY_API, query.scopeId(), null, context -> tasks.query(context, query)); }

    @PermissionResource(key = TianjiProcessPermissions.TASK_ADMIN_QUERY_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.task.adminQuery",
            allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = {HumanTaskClient.READ_SCOPE, HumanTaskClient.TRANSFER_SCOPE})
    @RequiresPermissions(codes = {TianjiProcessPermissions.TASK_READ, TianjiProcessPermissions.TASK_TRANSFER})
    @PostMapping(path = "/admin/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HumanTaskPageDTO> adminQuery(@RequestBody ListHumanTasksQuery query) { return execute(TianjiProcessPermissions.TASK_ADMIN_QUERY_API, query.scopeId(), null, context -> tasks.query(context, query)); }

    @PermissionResource(key = TianjiProcessPermissions.TASK_CLAIM_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.task.claim",
            allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = HumanTaskClient.CLAIM_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.TASK_CLAIM)
    @PostMapping(path = "/claim", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HumanTaskActionReceiptDTO> claim(@RequestBody ClaimHumanTaskCommand command) { return execute(TianjiProcessPermissions.TASK_CLAIM_API, command.taskId(), command.commandId(), context -> tasks.claim(context, command)); }

    @PermissionResource(key = TianjiProcessPermissions.TASK_UNCLAIM_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.task.unclaim",
            allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = HumanTaskClient.CLAIM_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.TASK_CLAIM)
    @PostMapping(path = "/unclaim", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HumanTaskActionReceiptDTO> unclaim(@RequestBody UnclaimHumanTaskCommand command) { return execute(TianjiProcessPermissions.TASK_UNCLAIM_API, command.taskId(), command.commandId(), context -> tasks.unclaim(context, command)); }

    @PermissionResource(key = TianjiProcessPermissions.TASK_TRANSFER_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.task.transfer",
            allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = HumanTaskClient.TRANSFER_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.TASK_TRANSFER)
    @PostMapping(path = "/transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HumanTaskActionReceiptDTO> transfer(@RequestBody TransferHumanTaskCommand command) { return execute(TianjiProcessPermissions.TASK_TRANSFER_API, command.taskId(), command.commandId(), context -> tasks.transfer(context, command)); }

    @PermissionResource(key = TianjiProcessPermissions.TASK_COMPLETE_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.task.complete",
            allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = HumanTaskClient.COMPLETE_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.TASK_COMPLETE)
    @PostMapping(path = "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HumanTaskActionReceiptDTO> complete(@RequestBody CompleteHumanTaskCommand command) { return execute(TianjiProcessPermissions.TASK_COMPLETE_API, command.taskId(), command.commandId(), context -> tasks.complete(context, command)); }

    private <T> ResponseEntity<T> execute(String resource, String reference, String commandId, Function<HumanTaskContext, T> operation) {
        HumanTaskContext context = null;
        try { context = access.require(resource); return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(operation.apply(context)); }
        catch (RuntimeException failure) {
            String referenceHash = reference == null ? null : ProcessRequestDigest.hash(List.of("TIANJI_HUMAN_TASK_AUDIT_REFERENCE_V1", resource, reference), Map.of());
            log.warn("Human task operation rejected: resource={}, referenceSha256={}, command={}, tenant={}, actor={}, stage={}, type={}", resource, referenceHash, commandId,
                    context == null ? null : context.actor().tenantId(), context == null ? null : context.userKey(), context == null ? "AUTHORIZATION" : "TASK", failure.getClass().getName());
            throw failure;
        }
    }
}
