package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceAdmission;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryClient;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionResultDTO;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Instant;
import java.util.Objects;

@RestController
@RequestMapping(path = MemoryClient.PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "memory.enabled", "memory.http.enabled", "memory.extraction.http.enabled"}, havingValue = "true")
public class MemoryPreferenceController {
    private final MemoryPreferenceAdmission admission;
    private final TianjiAgentAccess access;

    public MemoryPreferenceController(MemoryPreferenceAdmission admission, TianjiAgentAccess access) { this.admission = Objects.requireNonNull(admission, "admission"); this.access = Objects.requireNonNull(access, "access"); }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_EXTRACT_PREFERENCES_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.extractPreferences",
            allowedPrincipals = PermissionManifest.PrincipalType.USER,
            oauthScopes = {TianjiAgentPermissions.MEMORY_WRITE_SCOPE, "tianji:agent:read"})
    @RequiresPermissions(codes = {TianjiAgentPermissions.MEMORY_WRITE, TianjiAgentPermissions.READ})
    @PostMapping("/extract-preferences")
    public ResponseEntity<MemoryPreferenceExtractionResultDTO> extract(@RequestBody ExtractMemoryPreferencesCommand request) {
        var current = access.require(TianjiAgentPermissions.MEMORY_EXTRACT_PREFERENCES_API); Instant maximum = Instant.now().plusSeconds(30);
        var context = new MemoryOperationContext(current.actor(), current.expiresAt().isBefore(maximum) ? current.expiresAt() : maximum, null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(admission.extract(context, request));
    }
}
