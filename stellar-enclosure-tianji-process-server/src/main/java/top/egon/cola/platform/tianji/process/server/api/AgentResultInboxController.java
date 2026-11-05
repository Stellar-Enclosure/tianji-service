package top.egon.cola.platform.tianji.process.server.api;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.process.server.bridge.AgentResultInbox;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/tianji/v1/agent-result-inbox", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process", name = {"api.enabled", "agent-results.enabled"}, havingValue = "true")
public class AgentResultInboxController {
    private final AgentResultInbox inbox;
    private final TianjiProcessAccess access;
    private final AgentResultInboxReceiveObservation observation;

    public AgentResultInboxController(AgentResultInbox inbox, TianjiProcessAccess access) {
        this(inbox, access, AgentResultInboxReceiveObservation.disabled());
    }

    @Autowired
    public AgentResultInboxController(AgentResultInbox inbox, TianjiProcessAccess access, Environment environment,
                                      ObjectProvider<OpenTelemetry> telemetry) {
        this(inbox, access, environment.getProperty(AgentResultInboxReceiveObservation.FLAG, Boolean.class, false)
                ? AgentResultInboxReceiveObservation.enabled(telemetry::getIfUnique)
                : AgentResultInboxReceiveObservation.disabled());
    }

    AgentResultInboxController(AgentResultInbox inbox, TianjiProcessAccess access,
                               AgentResultInboxReceiveObservation observation) {
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.access = Objects.requireNonNull(access, "access");
        this.observation = Objects.requireNonNull(observation, "observation");
    }

    @PermissionResource(key = TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.agent.result.receive",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiProcessPermissions.AGENT_RESULT_RECEIVE_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.AGENT_RESULT_RECEIVE)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentResultReceipt> receive(@RequestBody AgentTerminalEvent event) {
        ProcessActor producer = access.require(TianjiProcessPermissions.AGENT_RESULT_RECEIVE_API);
        if (producer.subjectType() != ProcessActor.SubjectType.SERVICE) throw new AccessDeniedException("service identity required");
        var receipt = inbox.receive(producer, event);
        observation.afterReceive();
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(receipt);
    }
}
