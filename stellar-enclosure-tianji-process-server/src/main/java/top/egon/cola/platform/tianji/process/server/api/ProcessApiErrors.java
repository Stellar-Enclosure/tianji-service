package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.HandlerMapping;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import lombok.extern.slf4j.Slf4j;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessDecisionFailedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessTenantChangedException;
import top.egon.cola.platform.tianji.process.server.error.WorkerLeaseLostException;
import top.egon.cola.platform.tianji.process.server.error.ProcessCancellationBlockedException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessWorkerRecoveryBlockedException;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestControllerAdvice(basePackageClasses = ProcessController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
public final class ProcessApiErrors {
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

    @ExceptionHandler(AutomationOperationException.class)
    ResponseEntity<OrchestrationErrorDTO> automationError(AutomationOperationException failure) {
        HttpStatus status = switch (failure.code()) {
            case TIANJI_REQUEST_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case TIANJI_DRAFT_VERSION_CONFLICT, TIANJI_DRAFT_STATE_CONFLICT, TIANJI_DRAFT_SOURCE_UNAVAILABLE,
                    TIANJI_PROCESS_NOT_ACTIVATED, TIANJI_PROCESS_ACTIVATION_CONFLICT, TIANJI_EVENT_ROUTE_VERSION_CONFLICT,
                    TIANJI_NODE_RETRY_BLOCKED, TIANJI_EVENT_CONFLICT, TIANJI_EVENT_ROUTE_CHANGED -> HttpStatus.CONFLICT;
        };
        return error(status, failure.stableCode());
    }

    @ExceptionHandler(ProcessDecisionFailedException.class)
    ResponseEntity<OrchestrationErrorDTO> decisionFailed() { return error(HttpStatus.UNPROCESSABLE_CONTENT, "TIANJI_DECISION_FAILED"); }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<OrchestrationErrorDTO> invalid() { return error(HttpStatus.BAD_REQUEST, "TIANJI_INVALID_COMMAND"); }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<OrchestrationErrorDTO> invalidValidation() { return invalid(); }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<OrchestrationErrorDTO> invalidMethod(HandlerMethodValidationException failure) {
        return failure.isForReturnValue() ? error(HttpStatus.INTERNAL_SERVER_ERROR, "TIANJI_INTERNAL_ERROR") : invalid();
    }

    public static HandlerExceptionResolver automationWorkerMediaErrors(RequestMatcher route) {
        return strictCommandMediaErrors(route);
    }

    public static HandlerExceptionResolver strictCommandMediaErrors(RequestMatcher route) {
        return new AbstractHandlerExceptionResolver() {
            @Override
            protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
                                                      Object handler, Exception failure) {
                if (response.isCommitted() || !route.matches(request)) return null;
                if (failure instanceof HttpMediaTypeNotSupportedException) {
                    return closedMediaResponse(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "TIANJI_UNSUPPORTED_MEDIA_TYPE");
                }
                if (failure instanceof HttpMediaTypeNotAcceptableException) {
                    return closedMediaResponse(response, HttpServletResponse.SC_BAD_REQUEST, "TIANJI_INVALID_COMMAND");
                }
                return null;
            }

            @Override
            public int getOrder() {
                return -1;
            }
        };
    }

    /** Media selection can fail before MVC has selected a controller for package-scoped advice. */
    public static HandlerExceptionResolver closedJsonMediaErrors(RequestMatcher route) {
        return closedJsonMediaErrors(route, request -> false);
    }

    public static HandlerExceptionResolver closedJsonMediaErrors(RequestMatcher route, RequestMatcher acceptOnlyRoute) {
        return new AbstractHandlerExceptionResolver() {
            @Override
            protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
                                                      Object handler, Exception failure) {
                boolean unsupportedAccept = failure instanceof HttpMediaTypeNotAcceptableException
                        && (route.matches(request) || acceptOnlyRoute.matches(request));
                boolean unsupportedType = failure instanceof HttpMediaTypeNotSupportedException && route.matches(request);
                if ((!unsupportedAccept && !unsupportedType) || response.isCommitted()) return null;
                return closedMediaResponse(response, HttpServletResponse.SC_BAD_REQUEST, "TIANJI_INVALID_COMMAND");
            }

            @Override
            public int getOrder() {
                return -1;
            }
        };
    }

    private static ModelAndView closedMediaResponse(HttpServletResponse response, int status, String code) {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            response.getWriter().write("{\"code\":\"" + code + "\",\"correlationId\":null}");
        } catch (java.io.IOException unavailable) {
            throw new UncheckedIOException(unavailable);
        }
        return new ModelAndView();
    }

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
        if (request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler) {
            if (failure instanceof RejectedExecutionException
                    && (EngineTaskController.class.isAssignableFrom(handler.getBeanType())
                        || ProcessNodeExecutionController.class.isAssignableFrom(handler.getBeanType())
                        || WorkerController.class.isAssignableFrom(handler.getBeanType())
                        && handler.getMethod().getName().endsWith("AutomationWorkerTask"))) {
                return error(HttpStatus.TOO_MANY_REQUESTS, "TIANJI_CAPACITY_EXCEEDED");
            }
            if (ProcessActivationController.class.isAssignableFrom(handler.getBeanType())
                    || ProcessAutomationController.class.isAssignableFrom(handler.getBeanType())
                    && "startAutomationProcess".equals(handler.getMethod().getName())) {
                log.error("Process admission API request failed: type={}", failure.getClass().getName());
                return error(HttpStatus.SERVICE_UNAVAILABLE, "TIANJI_DEPENDENCY_UNAVAILABLE");
            }
            if (CaseController.class.isAssignableFrom(handler.getBeanType())) {
                if (failure instanceof RejectedExecutionException) return error(HttpStatus.TOO_MANY_REQUESTS, "TIANJI_CAPACITY_EXCEEDED");
                log.error("Case API request failed: type={}", failure.getClass().getName());
                return error(HttpStatus.SERVICE_UNAVAILABLE, "TIANJI_DEPENDENCY_UNAVAILABLE");
            }
        }
        log.error("Process API request failed: type={}", failure.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "TIANJI_INTERNAL_ERROR");
    }

    static ResponseEntity<OrchestrationErrorDTO> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(new OrchestrationErrorDTO(code, null));
    }
}
