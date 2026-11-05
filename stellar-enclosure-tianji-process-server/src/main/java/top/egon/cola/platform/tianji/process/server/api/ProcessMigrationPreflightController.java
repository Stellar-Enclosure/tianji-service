package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.beans.factory.annotation.Qualifier;
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
import top.egon.cola.platform.tianji.contract.orchestration.ProcessMigrationPreflightDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessMigrationPreflightDTO.Status;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessMigrationPreflightService.ValidateProcessMigrationQuery;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping(path = "/api/tianji/v1/process-instances/{instanceId}/migration-preflight",
        produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "stellar.tianji.process",
        name = {"api.enabled", "migration-preflight.enabled", "migration-preflight.http-enabled"}, havingValue = "true")
public class ProcessMigrationPreflightController {
    private static final Set<String> PARAMETERS = Set.of("targetReleaseVersion", "expectedTargetManifestSha256");

    private final ProcessMigrationPreflightService service;
    private final TianjiProcessAccess access;

    public ProcessMigrationPreflightController(@Qualifier("processMigrationPreflightService") ProcessMigrationPreflightService service,
            @Qualifier("tianjiProcessAccess") TianjiProcessAccess access) {
        this.service = Objects.requireNonNull(service, "service");
        this.access = Objects.requireNonNull(access, "access");
    }

    @PermissionResource(key = TianjiProcessPermissions.MIGRATION_PREFLIGHT_API, type = ResourceType.API,
            labelPage = "tianji.process.permissions", labelKey = "api.process.migrationPreflight",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = {TianjiProcessPermissions.READ_SCOPE, TianjiProcessPermissions.RELEASE_READ_SCOPE})
    @RequiresPermissions(codes = {TianjiProcessPermissions.READ, TianjiProcessPermissions.RELEASE_READ})
    @GetMapping
    public ResponseEntity<ProcessMigrationPreflightDTO> get(@PathVariable("instanceId") String instanceId,
                                                           @RequestParam MultiValueMap<String, String> parameters) {
        var actor = access.require(TianjiProcessPermissions.MIGRATION_PREFLIGHT_API);
        if (!PARAMETERS.equals(parameters.keySet()) || parameters.values().stream().anyMatch(values -> values.size() != 1)) {
            throw new IllegalArgumentException("Process migration preflight query is invalid");
        }
        var query = new ValidateProcessMigrationQuery(instanceId, parameters.getFirst("targetReleaseVersion"),
                parameters.getFirst("expectedTargetManifestSha256"));
        var report = service.inspectForResourceActor(actor, query);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicReport(query, report));
    }

    private static ProcessMigrationPreflightDTO publicReport(ValidateProcessMigrationQuery query,
            ProcessMigrationPreflightService.ProcessMigrationPreflightDTO report) {
        if (report == null || !query.instanceId().equals(report.instanceId())
                || !query.targetReleaseVersion().equals(report.targetReleaseVersion()) || report.status() == null) {
            throw unavailable();
        }
        try {
            return new ProcessMigrationPreflightDTO(report.instanceId(), report.sourceReleaseVersion(), report.targetReleaseVersion(),
                    Status.valueOf(report.status().name()), report.activityId(), report.observedAt());
        } catch (IllegalArgumentException malformed) {
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process migration preflight evidence is unavailable");
    }
}
