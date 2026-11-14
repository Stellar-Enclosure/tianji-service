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
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskClient;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EngineHumanTaskCompletionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EngineHumanTaskDTO;
import top.egon.cola.platform.tianji.process.server.security.TianjiHumanTaskAccess;
import top.egon.cola.platform.tianji.process.server.task.FlowableHumanTaskService;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest.PrincipalType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.IOException;

import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.ENGINE_TASK_COMPLETE_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.ENGINE_TASK_GET_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.TASK_COMPLETE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.TASK_READ;

@Slf4j
@Validated
@RestController("engineTaskController")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "human-tasks.enabled"}, havingValue = "true")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EngineTaskController {
    public static final String TASK_PATH = "/api/tianji/v2/process-tasks/{taskId}";
    public static final String COMPLETION_PATH = TASK_PATH + "/completions";
    private static final String PAGE = "tianji.process.permissions";
    private static final String TASK_ID = "[^\\p{Cc}]{1,255}";

    @Qualifier("tianjiHumanTaskAccess") private final TianjiHumanTaskAccess access;
    @Qualifier("flowableHumanTaskService") private final FlowableHumanTaskService tasks;

    @GetMapping(TASK_PATH)
    @PermissionResource(key = ENGINE_TASK_GET_API, type = ResourceType.API, labelPage = PAGE, labelKey = "api.engine.task.get",
            allowedPrincipals = PrincipalType.USER, oauthScopes = HumanTaskClient.READ_SCOPE)
    @RequiresPermissions(codes = TASK_READ)
    public ResponseEntity<EngineHumanTaskDTO> getEngineHumanTask(@PathVariable("taskId") @Pattern(regexp = TASK_ID) String taskId,
                                                               HttpServletRequest request) {
        requireTaskRequest(taskId, request);
        try {
            if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw invalid();
        } catch (IOException failure) {
            throw invalid();
        }
        var task = tasks.observeAction(access.require(ENGINE_TASK_GET_API), taskId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(task);
    }

    @PostMapping(path = COMPLETION_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PermissionResource(key = ENGINE_TASK_COMPLETE_API, type = ResourceType.API, labelPage = PAGE, labelKey = "api.engine.task.complete",
            allowedPrincipals = PrincipalType.USER, oauthScopes = HumanTaskClient.COMPLETE_SCOPE)
    @RequiresPermissions(codes = TASK_COMPLETE)
    public ResponseEntity<EngineHumanTaskCompletionDTO> completeEngineHumanTask(
            @PathVariable("taskId") @Pattern(regexp = TASK_ID) String taskId, @Valid @RequestBody CompleteEngineHumanTaskCommand command,
            HttpServletRequest request) {
        requireTaskRequest(taskId, request);
        var receipt = tasks.completeAction(access.require(ENGINE_TASK_COMPLETE_API), taskId, command);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt);
    }

    private static void requireTaskRequest(String taskId, HttpServletRequest request) {
        if (taskId.length() > 255 || taskId.isBlank() || !taskId.matches(TASK_ID) || !request.getParameterMap().isEmpty()) throw invalid();
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

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid automation task request");
    }
}
