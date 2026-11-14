package top.egon.cola.platform.tianji.process.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftPublicationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftValidationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ValidateProcessDraftQuery;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient.AuthorizationDeniedException;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionContext;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionServicePolicyGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3AuthenticationToken;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.starter.security.IdpAuthenticationToken;

import java.io.IOException;
import java.time.Instant;

import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_MODEL_VALIDATE_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_RELEASE_GET_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_RELEASE_PUBLISH_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.RELEASE_PUBLISH;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.RELEASE_PUBLISH_SCOPE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.RELEASE_READ;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.RELEASE_READ_SCOPE;

@Slf4j
@Validated
@RestController("processAutomationController")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessAutomationController {
    public static final String VALIDATE_PATH = "/api/tianji/v2/process-model-validations";
    public static final String RELEASES_PATH = "/api/tianji/v2/process-releases";
    public static final String RELEASE_PATH = RELEASES_PATH + "/{releaseVersion}";
    private static final String PAGE = "tianji.process.permissions";

    @Qualifier("tianjiProcessAccess") private final TianjiProcessAccess access;
    @Qualifier("processDraftService") private final ProcessDraftService drafts;
    @Qualifier("permissionServicePolicyGuard") private final PermissionServicePolicyGuard servicePolicies;

    @PermissionResource(key = AUTOMATION_MODEL_VALIDATE_API, type = ResourceType.API, labelPage = PAGE,
            labelKey = "api.automation.model.validate",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = RELEASE_PUBLISH)
    @PostMapping(path = VALIDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftValidationResultDTO> validateProcessModel(
            @Valid @RequestBody ValidateProcessDraftQuery query, HttpServletRequest request) {
        var owner = access.require(AUTOMATION_MODEL_VALIDATE_API);
        requireNoParameters(request);
        return noStore(drafts.validate(owner, query));
    }

    @PermissionResource(key = AUTOMATION_RELEASE_PUBLISH_API, type = ResourceType.API, labelPage = PAGE,
            labelKey = "api.automation.release.publish",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = RELEASE_PUBLISH)
    @PostMapping(path = RELEASES_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftPublicationVO> publishAutomationRelease(
            @Valid @RequestBody PublishProcessDraftCommand command, HttpServletRequest request) {
        var owner = access.require(AUTOMATION_RELEASE_PUBLISH_API);
        requireNoParameters(request);
        return noStore(drafts.publish(owner, command, publicationPermissionDeadline(owner)));
    }

    private Instant publicationPermissionDeadline(ProcessActor actor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Publication authorization must precede its write transaction");
        }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Current publication identity is required");
        }
        if (actor.subjectType() == ProcessActor.SubjectType.USER && auth instanceof Rbac3AuthenticationToken user) {
            var details = user.getPrincipal();
            var identity = details.identity();
            var snapshot = details.snapshot();
            var permissionView = snapshot.permissionSnapshot();
            if (permissionView == null) throw new IllegalStateException("Current publication permission evidence is unavailable");
            requireSameActor(actor, identity.subject(), identity.tenantId(), permissionView.issuer());
            return earlier(identity.expiresAt(), earlier(snapshot.expiresAt(), permissionView.snapshotExpiresAt()));
        }
        if (actor.subjectType() == ProcessActor.SubjectType.SERVICE && auth instanceof IdpAuthenticationToken token
                && token.getPrincipal() instanceof ServiceIdentityPrincipal identity) {
            try {
                requireSameActor(actor, identity.subject(), identity.tenantId(), token.verifiedIssuer().toString());
                var snapshot = servicePolicies.require(identity, AUTOMATION_RELEASE_PUBLISH_API);
                PermissionExecutionContext.verifyInput(AUTOMATION_RELEASE_PUBLISH_API, snapshot);
                return earlier(identity.expiresAt(), snapshot.expiresAt());
            } catch (AuthorizationDeniedException denied) {
                throw new AccessDeniedException("Current publication permission is denied");
            }
        }
        throw new AuthenticationCredentialsNotFoundException("Current publication identity is required");
    }

    private static void requireSameActor(ProcessActor actor, String subject, String tenant, String issuer) {
        if (!actor.subjectId().equals(subject) || !Long.toString(actor.tenantId()).equals(tenant)
                || !actor.equals(ProcessActor.verified(actor.tenantId(), actor.subjectType(), subject, issuer))) {
            throw new AccessDeniedException("Publication identity changed during authorization");
        }
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    @PermissionResource(key = AUTOMATION_RELEASE_GET_API, type = ResourceType.API, labelPage = PAGE,
            labelKey = "api.automation.release.get",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_READ_SCOPE)
    @RequiresPermissions(codes = RELEASE_READ)
    @GetMapping(RELEASE_PATH)
    public ResponseEntity<AutomationReleaseDTO> getAutomationRelease(
            @PathVariable("releaseVersion") @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") String releaseVersion,
            HttpServletRequest request) {
        var reader = access.require(AUTOMATION_RELEASE_GET_API);
        requireNoParameters(request);
        try {
            if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw invalidRequest();
        } catch (IOException unreadable) {
            throw invalidRequest();
        }
        return noStore(drafts.getAutomationRelease(reader, releaseVersion));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<OrchestrationErrorDTO> invalidInput() {
        return ProcessApiErrors.error(HttpStatus.BAD_REQUEST, "TIANJI_INVALID_COMMAND");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<OrchestrationErrorDTO> invalidMethod(HandlerMethodValidationException failure) {
        return failure.isForReturnValue()
                ? ProcessApiErrors.error(HttpStatus.INTERNAL_SERVER_ERROR, "TIANJI_INTERNAL_ERROR")
                : invalidInput();
    }

    private static void requireNoParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw invalidRequest();
    }

    private static IllegalArgumentException invalidRequest() {
        return new IllegalArgumentException("Invalid process automation request");
    }

    private static <T> ResponseEntity<T> noStore(T result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
