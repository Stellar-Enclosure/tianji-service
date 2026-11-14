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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DiscardProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftMutationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.*;

@Slf4j
@Validated
@RestController("processDraftController")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessDraftController {
    public static final String CREATE_PATH = "/api/tianji/v2/process-definitions/{definitionKey}/drafts";
    public static final String DRAFT_PATH = "/api/tianji/v2/process-drafts/{draftId}";
    public static final String LIST_PATH = "/api/tianji/v2/process-drafts";
    public static final String DISCARD_PATH = DRAFT_PATH + "/discard-commands";
    private static final String PAGE = "tianji.process.permissions";
    private static final Set<String> LIST_PARAMETERS = Set.of("definitionKey", "state", "limit", "cursor", "creationCommandId");

    @Qualifier("tianjiProcessAccess") private final TianjiProcessAccess access;
    @Qualifier("processDraftService") private final ProcessDraftService service;

    @PermissionResource(key = DRAFT_CREATE_API, type = ResourceType.API, labelPage = PAGE, labelKey = "api.draft.create",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = RELEASE_PUBLISH)
    @PostMapping(path = CREATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftSummaryDTO> create(@PathVariable("definitionKey") @Pattern(regexp = ProcessDraftService.DEFINITION_KEY) String definitionKey,
                                                  @Valid @RequestBody CreateProcessDraftCommand command, HttpServletRequest request) {
        var owner = access.require(DRAFT_CREATE_API);
        parameters(request, Set.of());
        if (command.getBaseReleaseVersion() != null) requireVisibleSource(owner);
        return noStore(service.create(owner, definitionKey, command));
    }

    private void requireVisibleSource(ProcessActor owner) {
        ProcessActor reader;
        try {
            reader = access.require(AUTOMATION_RELEASE_GET_API);
        } catch (AccessDeniedException denied) {
            throw new ProcessResourceNotFoundException();
        }
        if (!owner.equals(reader)) throw new ProcessResourceNotFoundException();
    }

    @PermissionResource(key = DRAFT_GET_API, type = ResourceType.API, labelPage = PAGE, labelKey = "api.draft.get",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = RELEASE_PUBLISH)
    @GetMapping(DRAFT_PATH)
    public ResponseEntity<ProcessDraftDTO> get(@PathVariable("draftId") @Pattern(regexp = ProcessDraftService.DRAFT_ID) String draftId,
                                               HttpServletRequest request) {
        var owner = access.require(DRAFT_GET_API);
        requireNoBody(request);
        var params = parameters(request, Set.of("commandId"));
        return noStore(service.get(owner, draftId, value(params, "commandId", ProcessDraftService.COMMAND_ID)));
    }

    @PermissionResource(key = DRAFT_SAVE_API, type = ResourceType.API, labelPage = PAGE, labelKey = "api.draft.save",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = RELEASE_PUBLISH)
    @PutMapping(path = DRAFT_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftMutationResultDTO> save(@PathVariable("draftId") @Pattern(regexp = ProcessDraftService.DRAFT_ID) String draftId,
                                                       @Valid @RequestBody SaveProcessDraftCommand command, HttpServletRequest request) {
        var owner = access.require(DRAFT_SAVE_API);
        parameters(request, Set.of());
        return noStore(service.save(owner, draftId, command));
    }

    @PermissionResource(key = DRAFT_LIST_API, type = ResourceType.API, labelPage = PAGE, labelKey = "api.draft.list",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = RELEASE_PUBLISH)
    @GetMapping(LIST_PATH)
    public ResponseEntity<ProcessDraftPageDTO> list(HttpServletRequest request) {
        var owner = access.require(DRAFT_LIST_API);
        requireNoBody(request);
        var params = parameters(request, LIST_PARAMETERS);
        String definitionKey = value(params, "definitionKey", ProcessDraftService.DEFINITION_KEY);
        String state = value(params, "state", "EDITING|PUBLISHED|DISCARDED");
        String rawLimit = value(params, "limit", "(?:[1-9][0-9]?|100)");
        String cursor = value(params, "cursor", "[A-Za-z0-9_-]{1,2048}");
        String create = value(params, "creationCommandId", ProcessDraftService.COMMAND_ID);
        if (create != null && (definitionKey != null || state != null || cursor != null)) throw invalid();
        return noStore(service.list(owner, definitionKey, state, rawLimit == null ? null : Integer.valueOf(rawLimit), cursor, create));
    }

    @PermissionResource(key = DRAFT_DISCARD_API, type = ResourceType.API, labelPage = PAGE, labelKey = "api.draft.discard",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = RELEASE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = RELEASE_PUBLISH)
    @PostMapping(path = DISCARD_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DraftMutationResultDTO> discard(@PathVariable("draftId") @Pattern(regexp = ProcessDraftService.DRAFT_ID) String draftId,
                                                          @Valid @RequestBody DiscardProcessDraftCommand command, HttpServletRequest request) {
        var owner = access.require(DRAFT_DISCARD_API);
        parameters(request, Set.of());
        return noStore(service.discard(owner, draftId, command));
    }

    private static Map<String, String[]> parameters(HttpServletRequest request, Set<String> allowed) {
        var parameters = request.getParameterMap();
        if (!allowed.containsAll(parameters.keySet()) || parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw invalid();
        }
        return parameters;
    }

    private static void requireNoBody(HttpServletRequest request) {
        try {
            if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw invalid();
        } catch (IOException unreadable) {
            throw invalid();
        }
    }

    private static String value(Map<String, String[]> parameters, String key, String allowed) {
        if (!parameters.containsKey(key)) return null;
        String value = parameters.get(key)[0];
        if (!value.matches(allowed)) throw invalid();
        return value;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<OrchestrationErrorDTO> invalidInput() {
        return error(HttpStatus.BAD_REQUEST, "TIANJI_INVALID_COMMAND");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<OrchestrationErrorDTO> invalidMethod(HandlerMethodValidationException failure) {
        return failure.isForReturnValue()
                ? error(HttpStatus.INTERNAL_SERVER_ERROR, "TIANJI_INTERNAL_ERROR")
                : invalidInput();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static ResponseEntity<OrchestrationErrorDTO> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(new OrchestrationErrorDTO(code, null));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid process draft request");
    }
}
