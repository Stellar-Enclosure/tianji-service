package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.recovery.TianjiAgentCheckpointResumeConfiguration.ResumeHost;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.TianjiCheckpointResumeGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiCheckpointResumeGrantHandoff;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Exact owner-only entry for the supported expired checkpoint recovery lane. */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "stellar.tianji.agent",
        name = {"api.enabled", "checkpoint-resume.api.enabled"}, havingValue = "true")
public class AgentCheckpointResumeController {
    public static final String PATH = "/api/tianji/v1/agent-checkpoints/resume";
    private static final Duration MAX_RESPONSE_WAIT = Duration.ofSeconds(35);

    private final TianjiAgentAccess access;
    private final ResumeHost host;
    private final TianjiCheckpointResumeGrantHandoff handoff;
    private final TianjiCheckpointResumeGrantAuthorizer authority;
    private final Duration responseWait;

    @Autowired
    public AgentCheckpointResumeController(TianjiAgentAccess access,
            @Qualifier("tianjiCheckpointResumeHost") ResumeHost host,
            @Qualifier("tianjiCheckpointResumeGrantHandoff") TianjiCheckpointResumeGrantHandoff handoff,
            @Qualifier("tianjiCheckpointResumeGrantAuthorizer") TianjiCheckpointResumeGrantAuthorizer authority) {
        this(access, host, handoff, authority, MAX_RESPONSE_WAIT);
    }

    AgentCheckpointResumeController(TianjiAgentAccess access, ResumeHost host,
            TianjiCheckpointResumeGrantHandoff handoff, TianjiCheckpointResumeGrantAuthorizer authority,
            Duration responseWait) {
        this.access = Objects.requireNonNull(access, "access");
        this.host = Objects.requireNonNull(host, "host");
        this.handoff = Objects.requireNonNull(handoff, "handoff");
        this.authority = Objects.requireNonNull(authority, "authority");
        if (!host.usesAuthorization(authority)) throw new IllegalStateException("Checkpoint RESUME host authority mismatch");
        if (responseWait == null || responseWait.isZero() || responseWait.isNegative()
                || responseWait.compareTo(MAX_RESPONSE_WAIT) > 0) throw new IllegalArgumentException("Invalid checkpoint RESUME wait");
        this.responseWait = responseWait;
    }

    @PermissionResource(key = TianjiAgentPermissions.CHECKPOINT_RESUME_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.checkpointResume",
            allowedPrincipals = PermissionManifest.PrincipalType.USER,
            oauthScopes = {TianjiAgentPermissions.CHECKPOINT_RESUME_SCOPE, TianjiAgentPermissions.READ_SCOPE})
    @RequiresPermissions(codes = {TianjiAgentPermissions.CHECKPOINT_RESUME, TianjiAgentPermissions.READ})
    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Acknowledgement> resume(@RequestBody ResumeCommand command,
            @RequestParam MultiValueMap<String, String> parameters) {
        var owner = access.require(TianjiAgentPermissions.CHECKPOINT_RESUME_API);
        if (owner.actor().subjectType() != TrustedAgentActor.SubjectType.USER) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Checkpoint RESUME requires a USER");
        }
        if (!parameters.isEmpty()) throw new IllegalArgumentException("Checkpoint resume query parameters are invalid");

        var stage = handoff.submit(owner, command, host, authority);
        try {
            // Timing out the response does not cancel or re-admit the worker.
            var acknowledgement = stage.toCompletableFuture().get(responseWait.toNanos(), TimeUnit.NANOSECONDS);
            if (acknowledgement == null || !command.runId().equals(acknowledgement.runId())
                    || acknowledgement.status() != AgentRunStatusEnum.RUNNING || acknowledgement.attempt() != 1
                    || acknowledgement.revision() != command.expectedRevision() + 2) throw unavailable();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(acknowledgement);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (TimeoutException timeout) {
            throw unavailable();
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof AgentLedgerException ledger) throw ledger;
            throw unavailable();
        }
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "Checkpoint RESUME acknowledgement is unavailable");
    }
}
