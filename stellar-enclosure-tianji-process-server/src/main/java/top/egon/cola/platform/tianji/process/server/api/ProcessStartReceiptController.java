package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping(path = ProcessStartReceiptController.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api",
        name = {"enabled", "start-receipt-read-enabled"}, havingValue = "true")
public class ProcessStartReceiptController {
    public static final String PATH = "/api/tianji/v1/process-start-receipts";
    private static final Set<String> QUERY_PARAMETERS = Set.of("commandId", "requestSha256");

    private final ProcessExecutionService processes;
    private final TianjiProcessAccess access;

    public ProcessStartReceiptController(ProcessExecutionService processes, TianjiProcessAccess access) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiProcessPermissions.START_RECEIPT_GET_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.process.startReceipt",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiProcessPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.READ)
    @GetMapping
    public ResponseEntity<ProcessStartReceiptDTO> receipt(@RequestParam MultiValueMap<String, String> parameters) {
        var actor = access.require(TianjiProcessPermissions.START_RECEIPT_GET_API);
        if (!parameters.keySet().equals(QUERY_PARAMETERS)
                || parameters.values().stream().anyMatch(values -> values.size() != 1)) {
            throw new IllegalArgumentException("Process START receipt query is invalid");
        }
        var query = new GetProcessStartReceiptQuery(parameters.getFirst("commandId"), parameters.getFirst("requestSha256"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(processes.getStartReceipt(actor, query));
    }
}
