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
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.memory.MemoryClient;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergePageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Instant;
import java.util.Objects;

@RestController
@RequestMapping(path = MemoryClient.PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "memory.enabled", "memory.http.enabled", "memory.merge-suggestions.http.enabled"}, havingValue = "true")
public class MemoryMergeController {
    private final PostgresMemoryService memory;
    private final TianjiAgentAccess access;

    public MemoryMergeController(PostgresMemoryService memory, TianjiAgentAccess access) { this.memory = Objects.requireNonNull(memory, "memory"); this.access = Objects.requireNonNull(access, "access"); }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_MERGE_SUGGESTIONS_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.mergeSuggestions",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE}, oauthScopes = TianjiAgentPermissions.MEMORY_READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.MEMORY_READ)
    @PostMapping("/merge-suggestions")
    public ResponseEntity<MemoryMergePageDTO> suggestions(@RequestBody MemoryMergeQuery request) {
        var current = access.require(TianjiAgentPermissions.MEMORY_MERGE_SUGGESTIONS_API); Instant maximum = Instant.now().plusSeconds(30);
        var context = new MemoryOperationContext(current.actor(), current.expiresAt().isBefore(maximum) ? current.expiresAt() : maximum, null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(memory.mergeSuggestions(context, request));
    }
}
