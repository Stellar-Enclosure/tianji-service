package top.egon.cola.platform.tianji.agent.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.server.integration.memory.MemoryPreferenceIntentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.memory.CancelMemoryPreferenceIntentCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryPreferenceIntentQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryClient;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentCancellationDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceIntentStatusDTO;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Instant;
import java.util.Objects;

@RestController
@RequestMapping(path = MemoryClient.PATH + "/preference-intents", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "memory.enabled", "memory.http.enabled", "memory.extraction.intents.http.enabled"}, havingValue = "true")
public class MemoryPreferenceIntentController {
    private final MemoryPreferenceIntentAccess intents;
    private final TianjiAgentAccess access;
    public MemoryPreferenceIntentController(MemoryPreferenceIntentAccess intents, TianjiAgentAccess access) { this.intents = Objects.requireNonNull(intents, "intents"); this.access = Objects.requireNonNull(access, "access"); }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_STATUS_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.preferenceIntentStatus", allowedPrincipals = PermissionManifest.PrincipalType.USER,
            oauthScopes = {TianjiAgentPermissions.MEMORY_READ_SCOPE, TianjiAgentPermissions.READ_SCOPE})
    @RequiresPermissions(codes = {TianjiAgentPermissions.MEMORY_READ, TianjiAgentPermissions.READ})
    @PostMapping("/status")
    public ResponseEntity<MemoryPreferenceIntentStatusDTO> status(@RequestBody GetMemoryPreferenceIntentQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(intents.status(context(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_STATUS_API), query));
    }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_CANCEL_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.cancelPreferenceIntent", allowedPrincipals = PermissionManifest.PrincipalType.USER,
            oauthScopes = {TianjiAgentPermissions.MEMORY_FORGET_SCOPE, TianjiAgentPermissions.READ_SCOPE})
    @RequiresPermissions(codes = {TianjiAgentPermissions.MEMORY_FORGET, TianjiAgentPermissions.READ})
    @PostMapping("/cancel")
    public ResponseEntity<MemoryPreferenceIntentCancellationDTO> cancel(@RequestBody CancelMemoryPreferenceIntentCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(intents.cancel(context(TianjiAgentPermissions.MEMORY_PREFERENCE_INTENT_CANCEL_API), command));
    }
    private MemoryOperationContext context(String resource) {
        var current = access.require(resource); Instant maximum = Instant.now().plusSeconds(30);
        return new MemoryOperationContext(current.actor(), current.expiresAt().isBefore(maximum) ? current.expiresAt() : maximum, null);
    }
}
