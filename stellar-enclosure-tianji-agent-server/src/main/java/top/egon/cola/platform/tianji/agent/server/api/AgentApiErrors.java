package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorDTO;
import top.egon.cola.platform.tianquan.jianshen.starter.client.Rbac3AuthorizationClient;

@RestControllerAdvice(basePackageClasses = AgentRunController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public final class AgentApiErrors {
    @ExceptionHandler(AgentLedgerException.class)
    ResponseEntity<AgentErrorDTO> ledger(AgentLedgerException failure) {
        HttpStatus status = switch (failure.code()) {
            case TIANJI_INVALID_COMMAND -> HttpStatus.BAD_REQUEST;
            case TIANJI_REQUEST_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case TIANJI_UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case TIANJI_PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case TIANJI_RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TIANJI_EVENT_CURSOR_EXPIRED -> HttpStatus.GONE;
            case TIANJI_IDEMPOTENCY_CONFLICT, TIANJI_DEFINITION_VERSION_CONFLICT, TIANJI_REVISION_CONFLICT,
                    TIANJI_LEASE_EXPIRED, TIANJI_SESSION_BUSY, TIANJI_RESULT_NOT_READY,
                    TIANJI_TENANT_CONTEXT_CHANGED, TIANJI_DISPATCH_EXPIRED, TIANJI_DISPATCH_CLOSED -> HttpStatus.CONFLICT;
            case TIANJI_CAPACITY_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case TIANJI_DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return error(status, failure.code());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<AgentErrorDTO> invalid() {
        return error(HttpStatus.BAD_REQUEST, AgentErrorCodeEnum.TIANJI_INVALID_COMMAND);
    }

    @ExceptionHandler(AgentRequestBodyAdvice.TooLarge.class)
    ResponseEntity<AgentErrorDTO> tooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE);
    }

    @ExceptionHandler(Rbac3AuthorizationClient.AuthorizationUnavailableException.class)
    ResponseEntity<AgentErrorDTO> authorizationUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<AgentErrorDTO> storageUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
    }

    private static ResponseEntity<AgentErrorDTO> error(HttpStatus status, AgentErrorCodeEnum code) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(new AgentErrorDTO(code, code.name(), null, null, null));
    }
}
