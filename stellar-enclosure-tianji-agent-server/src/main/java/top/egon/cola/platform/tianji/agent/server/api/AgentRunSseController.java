package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;
import java.io.IOException;

@RestController
@RequestMapping("/api/tianji/v1/agent-runs")
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public class AgentRunSseController {
    private final TianjiAgentAccess access;
    private final AgentRunEventStream streams;

    public AgentRunSseController(TianjiAgentAccess access, AgentRunEventStream streams) {
        this.access = Objects.requireNonNull(access, "access");
        this.streams = Objects.requireNonNull(streams, "streams");
    }

    @PermissionResource(key = TianjiAgentPermissions.EVENTS_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.events",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.READ)
    @GetMapping(path = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void events(@PathVariable String runId,
            @RequestParam(defaultValue = "0") long afterSequence, @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) throws IOException {
        TianjiAgentAccess.Authorized actor = access.require(TianjiAgentPermissions.EVENTS_API);
        long cursor = lastEventId == null || lastEventId.isBlank() ? afterSequence : Long.parseLong(lastEventId);
        streams.stream(actor, new AgentEventsQuery(runId, cursor, limit), response);
    }
}
