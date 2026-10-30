package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotsQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

/** Returns bounded preparation metadata only after current run-read authorization. */
@RestController
@RequestMapping(path = "/api/tianji/v1/agent-runs/{runId}/context-snapshots", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "context.snapshots.enabled"}, havingValue = "true")
public final class AgentContextSnapshotController {
    private final AgentLedgerService ledger;
    private final TianjiAgentAccess access;

    public AgentContextSnapshotController(AgentLedgerService ledger, TianjiAgentAccess access) { this.ledger = ledger; this.access = access; }

    @PermissionResource(key = TianjiAgentPermissions.CONTEXT_SNAPSHOTS_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.contextSnapshots",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.READ)
    @GetMapping
    public ResponseEntity<AgentContextSnapshotPageDTO> list(@PathVariable String runId, @RequestParam(defaultValue = "0") int afterModelCall,
                                                            @RequestParam(defaultValue = "5") int limit) {
        var actor = access.require(TianjiAgentPermissions.CONTEXT_SNAPSHOTS_API).actor();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ledger.contextSnapshots(actor, new AgentContextSnapshotsQuery(runId, afterModelCall, limit)));
    }
}
