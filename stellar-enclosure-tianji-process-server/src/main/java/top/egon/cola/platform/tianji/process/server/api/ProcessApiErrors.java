package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessDecisionFailedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.error.WorkerLeaseLostException;
import top.egon.cola.platform.tianji.process.server.error.ProcessCancellationBlockedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessWorkerRecoveryBlockedException;

@RestControllerAdvice(basePackageClasses = ProcessController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
public final class ProcessApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ProcessApiErrors.class);
    @ExceptionHandler(ProcessResourceNotFoundException.class)
    ResponseEntity<OrchestrationErrorDTO> notFound() { return error(HttpStatus.NOT_FOUND, "TIANJI_RESOURCE_NOT_FOUND"); }

    @ExceptionHandler(ProcessConflictException.class)
    ResponseEntity<OrchestrationErrorDTO> conflict() { return error(HttpStatus.CONFLICT, "TIANJI_IDEMPOTENCY_CONFLICT"); }

    @ExceptionHandler(ProcessVersionConflictException.class)
    ResponseEntity<OrchestrationErrorDTO> versionConflict() { return error(HttpStatus.CONFLICT, "TIANJI_PROCESS_VERSION_CONFLICT"); }

    @ExceptionHandler(ProcessCancellationBlockedException.class)
    ResponseEntity<OrchestrationErrorDTO> cancellationBlocked() { return error(HttpStatus.CONFLICT, "TIANJI_PROCESS_CANCEL_BLOCKED"); }

    @ExceptionHandler(ProcessWorkerRecoveryBlockedException.class)
    ResponseEntity<OrchestrationErrorDTO> recoveryBlocked() { return error(HttpStatus.CONFLICT, "TIANJI_PROCESS_WORKER_RECOVERY_BLOCKED"); }

    @ExceptionHandler(WorkerLeaseLostException.class)
    ResponseEntity<OrchestrationErrorDTO> workerLeaseLost() { return error(HttpStatus.CONFLICT, "TIANJI_WORKER_LEASE_LOST"); }

    @ExceptionHandler(ProcessTenantChangedException.class)
    ResponseEntity<OrchestrationErrorDTO> tenantChanged() { return error(HttpStatus.CONFLICT, "TIANJI_TENANT_CONTEXT_CHANGED"); }

    @ExceptionHandler(ProcessDecisionFailedException.class)
    ResponseEntity<OrchestrationErrorDTO> decisionFailed() { return error(HttpStatus.UNPROCESSABLE_CONTENT, "TIANJI_DECISION_FAILED"); }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<OrchestrationErrorDTO> invalid() { return error(HttpStatus.BAD_REQUEST, "TIANJI_INVALID_COMMAND"); }

    @ExceptionHandler({ProcessRequestBodyAdvice.TooLarge.class, CaseRequestBodyAdvice.TooLarge.class})
    ResponseEntity<OrchestrationErrorDTO> tooLarge() { return error(HttpStatus.PAYLOAD_TOO_LARGE, "TIANJI_REQUEST_TOO_LARGE"); }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<OrchestrationErrorDTO> unauthenticated() { return error(HttpStatus.UNAUTHORIZED, "TIANJI_UNAUTHENTICATED"); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<OrchestrationErrorDTO> denied() { return error(HttpStatus.FORBIDDEN, "TIANJI_PERMISSION_DENIED"); }

    @ExceptionHandler({DataAccessException.class, IllegalStateException.class,
            top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient.AuthorizationUnavailableException.class})
    ResponseEntity<OrchestrationErrorDTO> unavailable(RuntimeException failure) {
        log.warn("Process API dependency failure: type={}", failure.getClass().getName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "TIANJI_DEPENDENCY_UNAVAILABLE");
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<OrchestrationErrorDTO> unexpected(RuntimeException failure, HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler
                && CaseController.class.isAssignableFrom(handler.getBeanType())) {
            if (failure instanceof RejectedExecutionException) return error(HttpStatus.TOO_MANY_REQUESTS, "TIANJI_CAPACITY_EXCEEDED");
            log.error("Case API request failed: type={}", failure.getClass().getName());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "TIANJI_DEPENDENCY_UNAVAILABLE");
        }
        log.error("Process API request failed: type={}", failure.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "TIANJI_INTERNAL_ERROR");
    }

    private static ResponseEntity<OrchestrationErrorDTO> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(new OrchestrationErrorDTO(code, null));
    }
}
