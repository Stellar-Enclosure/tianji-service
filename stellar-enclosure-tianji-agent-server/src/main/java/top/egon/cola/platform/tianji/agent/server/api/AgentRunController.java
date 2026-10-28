package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/tianji/v1/agent-runs", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public class AgentRunController {
    private final AgentLedgerService ledger;
    private final TianjiAgentAccess access;

    public AgentRunController(AgentLedgerService ledger, TianjiAgentAccess access) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiAgentPermissions.START_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.start",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.START_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.START)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentRunDTO> start(@RequestBody StartAgentRunCommand command) {
        var actor = access.require(TianjiAgentPermissions.START_API).actor();
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ledger.start(actor, command));
    }

    @PermissionResource(key = TianjiAgentPermissions.GET_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.get",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.READ)
    @GetMapping("/{runId}")
    public ResponseEntity<AgentRunDTO> get(@PathVariable String runId) {
        var actor = access.require(TianjiAgentPermissions.GET_API).actor();
        return noStore(ledger.get(actor, new GetAgentRunQuery(runId)));
    }

    @PermissionResource(key = TianjiAgentPermissions.RESULT_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.result",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.READ)
    @GetMapping("/{runId}/result")
    public ResponseEntity<AgentExecutionResult> result(@PathVariable String runId) {
        var actor = access.require(TianjiAgentPermissions.RESULT_API).actor();
        AgentExecutionResult result = ledger.getResult(actor, new GetAgentRunQuery(runId))
                .orElseThrow(() -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESULT_NOT_READY,
                        "Agent result is not available"));
        return noStore(result);
    }

    @PermissionResource(key = TianjiAgentPermissions.CANCEL_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.cancel",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.CANCEL_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.CANCEL)
    @PostMapping(path = "/{runId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CancelAgentRunResult> cancel(@PathVariable String runId,
                                                       @RequestBody CancelAgentRunCommand command) {
        var actor = access.require(TianjiAgentPermissions.CANCEL_API).actor();
        if (!runId.equals(command.runId())) throw new IllegalArgumentException("run id mismatch");
        return noStore(ledger.cancel(actor, command));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
