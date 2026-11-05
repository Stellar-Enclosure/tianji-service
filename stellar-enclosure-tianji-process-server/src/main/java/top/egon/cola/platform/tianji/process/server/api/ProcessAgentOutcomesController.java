package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessAgentOutcomesQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessAgentOutcomePageDTO;
import top.egon.cola.platform.tianji.process.server.bridge.ProcessLinkedAgentOutcomeQuery;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/tianji/v1/process-instances", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process",
        name = {"api.enabled", "agent-results.enabled", "agent-results.owner-outcomes-enabled"}, havingValue = "true")
public class ProcessAgentOutcomesController {
    private final ProcessLinkedAgentOutcomeQuery query;
    private final TianjiProcessAccess access;

    public ProcessAgentOutcomesController(@Qualifier("processLinkedAgentOutcomeQuery") ProcessLinkedAgentOutcomeQuery query,
                                          TianjiProcessAccess access) {
        this.query = Objects.requireNonNull(query, "query");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiProcessPermissions.AGENT_OUTCOMES_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.agent.outcomes",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiProcessPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.READ)
    @GetMapping("/{instanceId}/agent-outcomes")
    public ResponseEntity<ProcessAgentOutcomePageDTO> outcomes(@PathVariable("instanceId") String instanceId,
                                                               @RequestParam MultiValueMap<String, String> parameters) {
        var actor = access.require(TianjiProcessPermissions.AGENT_OUTCOMES_API);
        if (parameters.size() > 1 || !parameters.isEmpty()
                && (!parameters.containsKey("cursor") || parameters.get("cursor").size() != 1)) {
            throw new IllegalArgumentException("Process Agent outcomes query is invalid");
        }
        var page = query.list(actor, new GetProcessAgentOutcomesQuery(instanceId, parameters.getFirst("cursor")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
    }
}
