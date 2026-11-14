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
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.AutomationActivationDTO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_ACTIVATION_LIST_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_RELEASE_ACTIVATE_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.RELEASE_ACTIVATE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.RELEASE_ACTIVATE_SCOPE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.RELEASE_READ;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.RELEASE_READ_SCOPE;

@Slf4j
@Validated
@RestController("processActivationController")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessActivationController {
    public static final String PATH = "/api/tianji/v2/process-definitions/{definitionKey}/activations";
    private static final String PAGE = "tianji.process.permissions";
    private static final String DEFINITION_KEY = "[A-Za-z_][A-Za-z0-9_.-]{0,127}";
    private static final Set<String> LIST_PARAMETERS = Set.of("commandId", "limit", "cursor");

    @Qualifier("tianjiProcessAccess") private final TianjiProcessAccess access;
    @Qualifier("automationActivationService") private final AutomationActivationService service;

    @PermissionResource(key = AUTOMATION_RELEASE_ACTIVATE_API, type = ResourceType.API, labelPage = PAGE,
            labelKey = "api.automation.release.activate", allowedPrincipals = PermissionManifest.PrincipalType.USER,
            oauthScopes = {RELEASE_READ_SCOPE, RELEASE_ACTIVATE_SCOPE})
    @RequiresPermissions(codes = {RELEASE_READ, RELEASE_ACTIVATE})
    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AutomationActivationDTO> activateAutomationRelease(
            @PathVariable("definitionKey") @Pattern(regexp = DEFINITION_KEY) String definitionKey,
            @Valid @RequestBody ActivationCommand command, HttpServletRequest request) {
        var actor = requireVerifiedActor(AUTOMATION_RELEASE_ACTIVATE_API);
        if (actor.subjectType() != ProcessActor.SubjectType.USER
                || !actor.equals(requireVerifiedActor(AUTOMATION_ACTIVATION_LIST_API))) {
            throw new AccessDeniedException("Activation requires matching USER permissions");
        }
        if (!definitionKey.matches(DEFINITION_KEY) || !request.getParameterMap().isEmpty()) throw invalid();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.activate(actor, definitionKey, command));
    }

    @PermissionResource(key = AUTOMATION_ACTIVATION_LIST_API, type = ResourceType.API, labelPage = PAGE,
            labelKey = "api.automation.activation.list",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_READ_SCOPE)
    @RequiresPermissions(codes = RELEASE_READ)
    @GetMapping(PATH)
    public ResponseEntity<ActivationPageDTO> listAutomationActivations(
            @PathVariable("definitionKey") @Pattern(regexp = DEFINITION_KEY) String definitionKey, HttpServletRequest request) {
        var actor = requireVerifiedActor(AUTOMATION_ACTIVATION_LIST_API);
        if (!definitionKey.matches(DEFINITION_KEY)) throw invalid();
        requireNoBody(request);
        var parameters = request.getParameterMap();
        if (!LIST_PARAMETERS.containsAll(parameters.keySet()) || parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw invalid();
        }
        String command = parameter(parameters, "commandId", "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
        String cursor = parameter(parameters, "cursor", "[A-Za-z0-9_-]{1,2048}");
        String limit = parameter(parameters, "limit", "(?:[1-9][0-9]?|100)");
        if (command != null && cursor != null) throw invalid();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(actor, definitionKey, command, limit == null ? null : Integer.valueOf(limit), cursor));
    }

    private ProcessActor requireVerifiedActor(String resource) {
        var actor = access.require(resource);
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AccessDeniedException("Activation requires a verified actor");
        }
        return actor;
    }

    private static void requireNoBody(HttpServletRequest request) {
        try {
            if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw invalid();
        } catch (IOException unreadable) {
            throw invalid();
        }
    }

    private static String parameter(Map<String, String[]> parameters, String name, String pattern) {
        if (!parameters.containsKey(name)) return null;
        String value = parameters.get(name)[0];
        if (!value.matches(pattern)) throw invalid();
        return value;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<OrchestrationErrorDTO> invalidInput() {
        return ProcessApiErrors.error(HttpStatus.BAD_REQUEST, "TIANJI_INVALID_COMMAND");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<OrchestrationErrorDTO> invalidMethod(HandlerMethodValidationException failure) {
        return failure.isForReturnValue()
                ? ProcessApiErrors.error(HttpStatus.SERVICE_UNAVAILABLE, "TIANJI_DEPENDENCY_UNAVAILABLE")
                : invalidInput();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid process activation request");
    }
}
