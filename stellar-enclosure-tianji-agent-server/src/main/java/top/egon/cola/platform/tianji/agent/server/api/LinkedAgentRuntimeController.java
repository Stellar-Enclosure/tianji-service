package top.egon.cola.platform.tianji.agent.server.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionGrantIssuer;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

@Slf4j
@RestController("linkedAgentRuntimeController")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "linked.enabled"}, havingValue = "true")
public class LinkedAgentRuntimeController {
    public static final String PATH = "/api/tianji/v2/agent-process-dispatches/submit";

    @Qualifier("agentLinkedSubmissionService")
    private final AgentLinkedSubmissionService submissions;
    @Qualifier("tianjiAgentAccess")
    private final TianjiAgentAccess access;
    @Qualifier("tianjiLinkedDispatchBindings")
    private final TianjiLinkedDispatchBindings bindings;
    @Qualifier("environment")
    private final Environment environment;
    @Qualifier("tianjiServiceExecutionGrantIssuer")
    private final ObjectProvider<TianjiServiceExecutionGrantIssuer> issuers;
    @Qualifier("tianjiSiExecutionBundleService")
    private final ObjectProvider<TianjiSiExecutionBundleService> bundles;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AgentErrorDTO> invalidCommand() {
        return AgentRuntimeTemplateController.invalidRuntimeCommandResponse();
    }

    @PermissionResource(key = TianjiAgentPermissions.LINKED_RUNTIME_SUBMIT_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.linked.runtimeSubmit",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiAgentPermissions.LINKED_SUBMIT_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.LINKED_SUBMIT)
    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LinkedAgentRuntimeDTO> submit(@Valid @RequestBody LinkedAgentRuntimeCommand command) {
        var authorized = access.require(TianjiAgentPermissions.LINKED_RUNTIME_SUBMIT_API);
        var bound = requireBoundTemplate(authorized, command);
        var issuer = issuers.getIfAvailable();
        try {
            var run = submissions.submitRuntimeV2(authorized.actor(), command, () -> issuer != null
                    && environment.getProperty(AgentRuntimeTemplateController.NEW_ADMISSION, Boolean.class, false),
                    template -> {
                        var preparation = bundles.getIfAvailable();
                        if (preparation == null) throw unavailable();
                        return preparation.prepare(authorized.actor(), template);
                    });
            if (!validRevision(run.revision())) throw unavailable();
            if (run.status() == AgentRunStatusEnum.QUEUED) {
                if (issuer == null) throw unavailable();
                issuer.ensureRuntime(authorized, bound.principal(), bound.binding(), command, run);
            }
            var query = new LinkedAgentLookupQuery(command.getIdempotencyKey(), command.getLink());
            var current = submissions.resolveRuntimeV2(authorized.actor(), query).orElseThrow(LinkedAgentRuntimeController::unavailable);
            if (current.closed()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DISPATCH_CLOSED, "Linked Agent dispatch is closed");
            }
            if (current.run() == null || !run.runId().equals(current.run().runId())
                    || !validRevision(current.run().revision())) throw unavailable();
            requireBoundTemplate(access.require(TianjiAgentPermissions.LINKED_RUNTIME_SUBMIT_API), command);
            var receipt = new LinkedAgentRuntimeDTO(run.runId(), command.getNodeExecutionRef(), current.run().status().name(),
                    current.run().revision(), command.getIdempotencyKey());
            return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(receipt);
        } catch (AgentLedgerException known) {
            if (known.code() == AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE) throw unavailable();
            throw known;
        }
    }

    private static boolean validRevision(long revision) {
        return revision >= 1 && revision <= ProcessTypedJson.V2_MAX_WIRE_INTEGER;
    }

    private TianjiLinkedDispatchBindings.Bound requireBoundTemplate(TianjiAgentAccess.Authorized authorized,
            LinkedAgentRuntimeCommand command) {
        var bound = bindings.require(authorized, command.getLink(), null);
        if (!command.getTemplateSha256().equals(command.getLink().definitionContentSha256())
                || bound.binding().definitions().stream().noneMatch(definition -> definition.key().equals(command.getTemplateKey())
                        && definition.version() == command.getTemplateVersion()
                        && definition.contentSha256().equals(command.getTemplateSha256()))) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Linked Agent runtime template denied");
        }
        return bound;
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "Linked Agent runtime submission is unavailable");
    }
}
