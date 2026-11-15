package top.egon.cola.platform.tianji.agent.server.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceAdmission;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryClient;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionResultDTO;
import top.egon.cola.platform.tianji.contract.memory.PrepareMemoryPreferencesQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferencePreparationDTO;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Instant;

@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController("memoryPreferenceController")
@RequestMapping(path = MemoryClient.PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "memory.enabled", "memory.http.enabled", "memory.extraction.http.enabled"}, havingValue = "true")
public class MemoryPreferenceController {
    @Qualifier("tianjiMemoryPreferenceAdmission")
    private final MemoryPreferenceAdmission admission;
    @Qualifier("tianjiAgentAccess")
    private final TianjiAgentAccess access;

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_PREPARE_PREFERENCES_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.preparePreferences", allowedPrincipals = PermissionManifest.PrincipalType.USER,
            oauthScopes = {TianjiAgentPermissions.MEMORY_WRITE_SCOPE, "tianji:agent:read"})
    @RequiresPermissions(codes = {TianjiAgentPermissions.MEMORY_WRITE, TianjiAgentPermissions.READ})
    @PostMapping("/prepare-preferences")
    public ResponseEntity<MemoryPreferencePreparationDTO> prepare(@Valid @RequestBody PrepareMemoryPreferencesQuery request) {
        access.require(TianjiAgentPermissions.MEMORY_PREPARE_PREFERENCES_API);
        throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Memory preference preparation has retired");
    }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_EXTRACT_PREFERENCES_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.extractPreferences",
            allowedPrincipals = PermissionManifest.PrincipalType.USER,
            oauthScopes = {TianjiAgentPermissions.MEMORY_WRITE_SCOPE, "tianji:agent:read"})
    @RequiresPermissions(codes = {TianjiAgentPermissions.MEMORY_WRITE, TianjiAgentPermissions.READ})
    @PostMapping("/extract-preferences")
    public ResponseEntity<MemoryPreferenceExtractionResultDTO> extract(@Valid @RequestBody ExtractMemoryPreferencesCommand request) {
        var current = access.require(TianjiAgentPermissions.MEMORY_EXTRACT_PREFERENCES_API); Instant maximum = Instant.now().plusSeconds(30);
        var context = new MemoryOperationContext(current.actor(), current.expiresAt().isBefore(maximum) ? current.expiresAt() : maximum, null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(admission.replayCommittedExtraction(context, request));
    }
}
