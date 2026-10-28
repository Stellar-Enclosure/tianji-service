package top.egon.cola.platform.tianji.process.server.api;

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
import top.egon.cola.platform.tianji.contract.orchestration.DecisionResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.EvaluateDecisionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessReleaseQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.process.server.decision.ProcessDecisionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/tianji/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
public class ProcessController {
    private final ProcessReleaseService releases;
    private final ProcessExecutionService processes;
    private final ProcessDecisionService decisions;
    private final TianjiProcessAccess access;

    public ProcessController(ProcessReleaseService releases, ProcessExecutionService processes,
                             ProcessDecisionService decisions, TianjiProcessAccess access) {
        this.releases = Objects.requireNonNull(releases, "releases");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.decisions = Objects.requireNonNull(decisions, "decisions");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiProcessPermissions.RELEASE_PUBLISH_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.release.publish",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiProcessPermissions.RELEASE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.RELEASE_PUBLISH)
    @PostMapping(path = "/process-releases", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProcessReleaseDTO> publish(@RequestBody PublishProcessReleaseCommand command) {
        var actor = access.require(TianjiProcessPermissions.RELEASE_PUBLISH_API);
        var models = command.models().stream().map(model -> new ProcessReleaseService.ModelResource(
                Type.valueOf(model.type().name()), model.definitionKey(), model.xml().getBytes(StandardCharsets.UTF_8),
                model.inputs().entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> ValueType.valueOf(entry.getValue().name()))))).toList();
        return noStore(release(releases.publish(actor, command.releaseVersion(), models)));
    }

    @PermissionResource(key = TianjiProcessPermissions.RELEASE_GET_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.release.get",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiProcessPermissions.RELEASE_READ_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.RELEASE_READ)
    @GetMapping("/process-releases/{releaseVersion}")
    public ResponseEntity<ProcessReleaseDTO> release(@PathVariable("releaseVersion") String releaseVersion) {
        var actor = access.require(TianjiProcessPermissions.RELEASE_GET_API);
        return noStore(release(releases.get(actor, new GetProcessReleaseQuery(releaseVersion).releaseVersion())));
    }

    @PermissionResource(key = TianjiProcessPermissions.START_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.process.start",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiProcessPermissions.START_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.START)
    @PostMapping(path = "/process-instances", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProcessInstanceDTO> start(@RequestBody StartProcessCommand command) {
        var actor = access.require(TianjiProcessPermissions.START_API);
        var input = new ProcessExecutionService.StartCommand(command.releaseVersion(), command.definitionKey(),
                command.businessKey(), command.commandId(), command.correlationId(), command.variables());
        return noStore(instance(processes.start(actor, input)));
    }

    @PermissionResource(key = TianjiProcessPermissions.GET_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.process.get",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiProcessPermissions.READ_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.READ)
    @GetMapping("/process-instances/{instanceId}")
    public ResponseEntity<ProcessInstanceDTO> instance(@PathVariable("instanceId") String instanceId) {
        var actor = access.require(TianjiProcessPermissions.GET_API);
        return noStore(instance(processes.get(actor, new GetProcessInstanceQuery(instanceId).instanceId())));
    }

    @PermissionResource(key = TianjiProcessPermissions.DECISION_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.decision.evaluate",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiProcessPermissions.DECISION_SCOPE)
    @RequiresPermissions(codes = TianjiProcessPermissions.DECISION_EXECUTE)
    @PostMapping(path = "/decisions/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DecisionResultDTO> evaluate(@RequestBody EvaluateDecisionCommand command) {
        var actor = access.require(TianjiProcessPermissions.DECISION_API);
        var input = new ProcessDecisionService.EvaluateCommand(command.releaseVersion(), command.decisionKey(), command.requestId(),
                command.correlationId(), command.inputs());
        var result = decisions.evaluate(actor, input);
        return noStore(new DecisionResultDTO(result.executionId(), result.decisionKey(), result.releaseKey(), result.correlationId(),
                result.submittedAt(), result.outputs(), result.matchedRuleIds()));
    }

    private static ProcessReleaseDTO release(ProcessReleaseService.Release release) {
        var models = release.definitions().stream().map(definition -> new ProcessModelDTO(
                ProcessModelTypeEnum.valueOf(definition.type().name()), definition.definitionKey(), definition.modelSha256(),
                definition.inputs().entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> ProcessValueTypeEnum.valueOf(entry.getValue().name()))))).toList();
        return new ProcessReleaseDTO(release.releaseKey(), release.manifestSha256(), release.createdAt(), models);
    }

    private static ProcessInstanceDTO instance(ProcessExecutionService.Snapshot snapshot) {
        return new ProcessInstanceDTO(snapshot.instanceId(), snapshot.definitionKey(), snapshot.releaseKey(), snapshot.businessKey(),
                snapshot.correlationId(), ProcessInstanceStatusEnum.valueOf(snapshot.state().name()), snapshot.submittedAt(), snapshot.finishedAt());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
