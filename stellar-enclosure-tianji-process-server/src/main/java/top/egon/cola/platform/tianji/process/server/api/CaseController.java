package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.ActOnPlanItemCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;
import top.egon.cola.platform.tianji.contract.orchestration.StartCaseCommand;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.security.TianjiCaseAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

/** Private Case operations use the existing fixed contract and trusted current operation context. */
@RestController
@RequestMapping(path = "/api/tianji/v1/cases", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "cases.enabled"}, havingValue = "true")
public class CaseController {
    private final FlowableCaseService cases;
    private final TianjiCaseAccess access;
    public CaseController(FlowableCaseService cases, TianjiCaseAccess access) { this.cases = Objects.requireNonNull(cases, "cases"); this.access = Objects.requireNonNull(access, "access"); }
    @PermissionResource(key = TianjiProcessPermissions.CASE_START_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.case.start",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE}, oauthScopes = TianjiProcessPermissions.CASE_START_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.CASE_START)
    @PostMapping(path = "/start", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CaseInstanceDTO> start(@RequestBody StartCaseCommand command) {
        var context = access.require(TianjiProcessPermissions.CASE_START_API);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(cases.start(context, command));
    }
    @PermissionResource(key = TianjiProcessPermissions.CASE_GET_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.case.get",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE}, oauthScopes = TianjiProcessPermissions.CASE_READ_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.CASE_READ)
    @PostMapping(path = "/get", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CaseInstanceDTO> get(@RequestBody CaseInstanceQuery query) {
        var context = access.require(TianjiProcessPermissions.CASE_GET_API);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(cases.get(context, query));
    }
    @PermissionResource(key = TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.case.actPlanItem",
            allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.CASE_PLAN_ITEM_ACT)
    @PostMapping(path = "/act-plan-item", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CaseOperationResult> actOnPlanItem(@RequestBody ActOnPlanItemCommand command) {
        var context = access.require(TianjiProcessPermissions.CASE_PLAN_ITEM_ACT_API);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(cases.actOnPlanItem(context, command));
    }
}
