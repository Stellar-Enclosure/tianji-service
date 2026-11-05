package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryDispositionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryDispositionDTO.AgentEffect;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryDispositionDTO.Lifecycle;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryDispositionDTO.Reason;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryDispositionDTO.ToolCoverage;
import top.egon.cola.platform.tianji.contract.agent.AgentRecoveryDispositionDTO.ToolEvidenceDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/tianji/v1/agent-runs/{runId}/recovery-disposition", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "stellar.tianji.agent",
        name = {"api.enabled", "storage.enabled", "recovery-diagnostics.enabled"}, havingValue = "true")
public class AgentRecoveryDispositionController {
    private final AgentLedgerService ledger;
    private final TianjiAgentAccess access;

    public AgentRecoveryDispositionController(AgentLedgerService ledger, TianjiAgentAccess access) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiAgentPermissions.RECOVERY_DISPOSITION_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.recoveryDisposition",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.READ)
    @GetMapping
    public ResponseEntity<AgentRecoveryDispositionDTO> get(@PathVariable("runId") String runId,
                                                           @RequestParam MultiValueMap<String, String> parameters) {
        var actor = access.require(TianjiAgentPermissions.RECOVERY_DISPOSITION_API).actor();
        var query = new GetAgentRunQuery(runId);
        if (!parameters.isEmpty() || !runId.equals(query.runId())) {
            throw new IllegalArgumentException("Agent recovery query is invalid");
        }
        var evidence = ledger.inspectRecovery(actor, query);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicEvidence(query.runId(), evidence));
    }

    private static AgentRecoveryDispositionDTO publicEvidence(String runId, AgentLedgerService.AgentRecoveryDispositionDTO evidence) {
        if (evidence == null || !runId.equals(evidence.runId()) || evidence.lifecycle() == null || evidence.reason() == null
                || evidence.agentEffect() == null || evidence.toolEvidence() == null || evidence.toolEvidence().coverage() == null) {
            throw unavailable();
        }
        try {
            var tool = evidence.toolEvidence();
            return new AgentRecoveryDispositionDTO(runId, evidence.observedAt(), Lifecycle.valueOf(evidence.lifecycle().name()),
                    Reason.valueOf(evidence.reason().name()), AgentEffect.valueOf(evidence.agentEffect().name()),
                    new ToolEvidenceDTO(ToolCoverage.valueOf(tool.coverage().name()), tool.hasDispatching(),
                            tool.hasUnknownResult(), tool.hasUnresolvedWrite(), tool.hasLocalReceipt()), evidence.evidenceConflict());
        } catch (IllegalArgumentException malformed) {
            throw unavailable();
        }
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent recovery evidence is unavailable");
    }
}
