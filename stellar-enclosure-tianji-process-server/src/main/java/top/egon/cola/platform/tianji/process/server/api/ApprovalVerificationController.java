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
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;
import top.egon.cola.platform.tianji.process.server.identity.ApprovalVerificationContext;
import top.egon.cola.platform.tianji.process.server.security.TianjiApprovalVerificationAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianji.process.server.task.ApprovalVerificationService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping(path = ApprovalClient.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "approval-verification.enabled"}, havingValue = "true")
public class ApprovalVerificationController {
    private static final Logger log = LoggerFactory.getLogger(ApprovalVerificationController.class);
    private final ApprovalVerificationService approvals;
    private final TianjiApprovalVerificationAccess access;
    public ApprovalVerificationController(ApprovalVerificationService approvals, TianjiApprovalVerificationAccess access) { this.approvals = Objects.requireNonNull(approvals, "approvals"); this.access = Objects.requireNonNull(access, "access"); }

    @PermissionResource(key = TianjiProcessPermissions.APPROVAL_VERIFY_API, type = ResourceType.API, labelPage = "tianji.process.permissions", labelKey = "api.task.approval.verify",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = ApprovalClient.VERIFY_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.APPROVAL_VERIFY)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerifiedApprovalDTO> verify(@RequestBody VerifyApprovalQuery query) {
        ApprovalVerificationContext context = null;
        try { context = access.require(); return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(approvals.verify(context, query)); }
        catch (RuntimeException failure) {
            String consumer = context == null ? null : OrchestrationRequestDigests.hash(List.of("TIANJI_APPROVAL_CONSUMER_AUDIT_V1", Long.toString(context.actor().tenantId()), context.actor().issuerFingerprint(), context.actor().subjectId()), Map.of());
            log.warn("Approval verification rejected: policy={}, requestSha256={}, consumerSha256={}, stage={}, type={}", query.policyRef(), query.fingerprint(), consumer, context == null ? "AUTHORIZATION" : "PROVENANCE", failure.getClass().getName());
            throw failure;
        }
    }
}
