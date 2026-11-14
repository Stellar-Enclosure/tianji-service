package top.egon.cola.platform.tianji.process.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.event.ProcessEventApplicationService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.AutomationObservationPermitVO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest.PrincipalType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.IOException;

import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.EVENT_INGEST;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.EVENT_INGEST_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.EVENT_INGEST_SCOPE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.EVENT_RECEIPT_GET_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.OBSERVE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.OBSERVE_SCOPE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.READ;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.READ_SCOPE;

@Slf4j
@Validated
@RestController("processEventController")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "events.enabled"}, havingValue = "true")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessEventController {
    public static final String EVENTS_PATH = "/api/tianji/v2/process-events";
    public static final String RECEIPT_PATH = "/api/tianji/v2/process-event-receipts/{receiptId}";
    private static final String LABEL_PAGE = "tianji.process.permissions";
    private static final String RECEIPT_ID = "[A-Za-z][A-Za-z0-9_.:-]{0,127}";

    @Qualifier("tianjiProcessAccess") private final TianjiProcessAccess access;
    @Qualifier("processEventApplicationService") private final ProcessEventApplicationService events;
    @Qualifier("processExecutionService") private final ProcessExecutionService processes;

    @PermissionResource(key = EVENT_INGEST_API, type = ResourceType.API, labelPage = LABEL_PAGE,
            labelKey = "api.event.ingest", allowedPrincipals = PrincipalType.SERVICE, oauthScopes = EVENT_INGEST_SCOPE)
    @RequiresPermissions(codes = EVENT_INGEST)
    @PostMapping(path = EVENTS_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProcessEventReceiptDTO> receiveProcessEvent(@Valid @RequestBody ProcessEventCommand command,
                                                                    HttpServletRequest request) {
        var producer = access.require(EVENT_INGEST_API);
        if (producer.subjectType() != ProcessActor.SubjectType.SERVICE) {
            throw new AccessDeniedException("A trusted SERVICE producer is required");
        }
        requireNoParameters(request);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(events.receive(producer, command));
    }

    @PermissionResource(key = EVENT_RECEIPT_GET_API, type = ResourceType.API, labelPage = LABEL_PAGE,
            labelKey = "api.event.receipt.get", allowedPrincipals = {PrincipalType.USER, PrincipalType.SERVICE},
            oauthScopes = {EVENT_INGEST_SCOPE, READ_SCOPE, OBSERVE_SCOPE})
    @RequiresPermissions(mode = PermissionMode.ANY, codes = {EVENT_INGEST, READ, OBSERVE})
    @GetMapping(RECEIPT_PATH)
    public ResponseEntity<ProcessEventReceiptDTO> getProcessEventReceipt(
            @PathVariable("receiptId") @Pattern(regexp = RECEIPT_ID) String receiptId, HttpServletRequest request) {
        var actor = access.require(EVENT_RECEIPT_GET_API);
        requireNoParameters(request);
        requireNoBody(request);
        if (!receiptId.matches(RECEIPT_ID)) throw invalid();
        var receipt = events.observe(actor, receiptId, instanceId -> canObserveInstance(actor, instanceId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt);
    }

    private boolean canObserveInstance(ProcessActor actor, String instanceId) {
        try {
            var current = access.observeAutomationInstance();
            if (!actor.equals(current.actor())) return false;
            var permit = current.permit();
            var metadataOnly = new AutomationObservationPermitVO(permit.ownerRead(), permit.observe(), false, false, false);
            processes.observeAutomation(actor, instanceId, metadataOnly);
            return true;
        } catch (AccessDeniedException | ProcessResourceNotFoundException denied) {
            return false;
        }
    }

    private static void requireNoParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) throw invalid();
    }

    private static void requireNoBody(HttpServletRequest request) {
        try {
            if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw invalid();
        } catch (IOException unavailable) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid process event request");
    }
}
