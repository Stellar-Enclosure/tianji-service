package top.egon.cola.platform.tianji.agent.server.api;

import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedDispatchProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiLinkedServiceAdmission;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentCloseResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartResult;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

@RestController
@RequestMapping(path = "/api/tianji/v1/agent-process-dispatches", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.linked", name = "enabled", havingValue = "true")
public class LinkedAgentDispatchController {
    private final AgentLinkedSubmissionService submissions;
    private final TianjiAgentAccess access;
    private final TianjiLinkedDispatchBindings bindings;
    private final TianjiLinkedServiceAdmission admission;

    public LinkedAgentDispatchController(AgentLinkedSubmissionService submissions, TianjiAgentAccess access,
                                        TianjiLinkedDispatchBindings bindings, TianjiLinkedDispatchProperties properties,
                                        ObjectProvider<TianjiLinkedServiceAdmission> admissions) {
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.access = Objects.requireNonNull(access, "access");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.admission = properties.submissionEnabled() ? admissions.getIfAvailable() : null;
        if (properties.submissionEnabled() && admission == null) throw new IllegalStateException("TIANJI_LINKED_SERVICE_ADMISSION_REQUIRED");
    }

    @PermissionResource(key = TianjiAgentPermissions.LINKED_SUBMIT_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.linked.submit",
            allowedPrincipals = {PermissionManifest.PrincipalType.SERVICE}, oauthScopes = TianjiAgentPermissions.LINKED_SUBMIT_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.LINKED_SUBMIT)
    @PostMapping(path = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LinkedAgentStartResult> submit(@RequestBody LinkedAgentStartCommand command) {
        var authorized = access.require(TianjiAgentPermissions.LINKED_SUBMIT_API);
        bindings.require(authorized, command.link(), null);
        if (admission == null) throw unavailable();
        var bound = bindings.require(authorized, command.link(), command);
        var run = submissions.submit(authorized.actor(), command);
        try { admission.ensure(authorized, bound.principal(), bound.binding(), command, run); }
        catch (AgentLedgerException known) { throw known; }
        catch (RuntimeException failure) { throw unavailable(); }
        var query = new LinkedAgentLookupQuery(command.start().idempotencyKey(), command.link());
        var current = submissions.resolve(authorized.actor(), query).orElseThrow(LinkedAgentDispatchController::unavailable);
        if (current.closed()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DISPATCH_CLOSED, "Linked Agent dispatch is closed");
        if (current.run() == null || !run.runId().equals(current.run().runId())) throw unavailable();
        bindings.require(authorized, command.link(), command);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new LinkedAgentStartResult(query, current.run()));
    }

    @PermissionResource(key = TianjiAgentPermissions.LINKED_RESOLVE_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.linked.resolve",
            allowedPrincipals = {PermissionManifest.PrincipalType.SERVICE}, oauthScopes = TianjiAgentPermissions.LINKED_RESOLVE_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.LINKED_RESOLVE)
    @PostMapping(path = "/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LinkedAgentLookupResult> resolve(@RequestBody LinkedAgentLookupQuery query) {
        var authorized = access.require(TianjiAgentPermissions.LINKED_RESOLVE_API);
        bindings.require(authorized, query.link(), null);
        var result = submissions.resolve(authorized.actor(), query);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new LinkedAgentLookupResult(query, result.isPresent(), result.orElse(null)));
    }

    @PermissionResource(key = TianjiAgentPermissions.LINKED_CLOSE_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.linked.close",
            allowedPrincipals = {PermissionManifest.PrincipalType.SERVICE}, oauthScopes = TianjiAgentPermissions.LINKED_CLOSE_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.LINKED_CLOSE)
    @PostMapping(path = "/close", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LinkedAgentCloseResult> close(@RequestBody LinkedAgentLookupQuery query) {
        var authorized = access.require(TianjiAgentPermissions.LINKED_CLOSE_API);
        bindings.require(authorized, query.link(), null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new LinkedAgentCloseResult(query, submissions.close(authorized.actor(), query)));
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Linked SERVICE execution authorization is unavailable");
    }
}
