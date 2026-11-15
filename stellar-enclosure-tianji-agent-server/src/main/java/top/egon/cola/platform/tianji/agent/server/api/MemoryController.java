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
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryClient;
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidateQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidatePageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalPageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Instant;

/** POST queries keep private semantic input and owner references out of URLs; every result is scoped and non-cacheable. */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@RestController("memoryController")
@RequestMapping(path = MemoryClient.PATH, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.agent", name = {"api.enabled", "memory.enabled", "memory.http.enabled"}, havingValue = "true")
public class MemoryController {
    @Qualifier("tianjiMemoryService")
    private final PostgresMemoryService memory;
    @Qualifier("tianjiAgentAccess")
    private final TianjiAgentAccess access;

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_GET_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.get",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.MEMORY_READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.MEMORY_READ)
    @PostMapping("/get")
    public ResponseEntity<MemoryEntryDTO> get(@RequestBody GetMemoryQuery request) {
        return noStore(memory.get(context(TianjiAgentPermissions.MEMORY_GET_API), request));
    }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_RETRIEVE_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.retrieve",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.MEMORY_READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.MEMORY_READ)
    @PostMapping("/retrieve")
    public ResponseEntity<MemoryRetrievalPageDTO> retrieve(@RequestBody MemoryRetrievalQuery request) {
        return noStore(memory.retrieve(context(TianjiAgentPermissions.MEMORY_RETRIEVE_API), request));
    }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_CANDIDATES_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.candidates",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.MEMORY_READ_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.MEMORY_READ)
    @PostMapping("/candidates")
    public ResponseEntity<MemoryCandidatePageDTO> candidates(@RequestBody MemoryCandidateQuery request) {
        return noStore(memory.candidates(context(TianjiAgentPermissions.MEMORY_CANDIDATES_API), request));
    }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_UPSERT_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.upsert",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.MEMORY_WRITE_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.MEMORY_WRITE)
    @PostMapping("/upsert")
    public ResponseEntity<MemoryMutationResultDTO> upsert(@Valid @RequestBody UpsertMemoryCommand request) {
        return noStore(memory.replayCommittedUpsert(context(TianjiAgentPermissions.MEMORY_UPSERT_API), request));
    }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_CORRECT_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.correct",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.MEMORY_WRITE_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.MEMORY_WRITE)
    @PostMapping("/correct")
    public ResponseEntity<MemoryMutationResultDTO> correct(@Valid @RequestBody CorrectMemoryCommand request) {
        return noStore(memory.replayCommittedCorrection(context(TianjiAgentPermissions.MEMORY_CORRECT_API), request));
    }

    @PermissionResource(key = TianjiAgentPermissions.MEMORY_FORGET_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.memory.forget",
            allowedPrincipals = {PermissionManifest.PrincipalType.USER, PermissionManifest.PrincipalType.SERVICE},
            oauthScopes = TianjiAgentPermissions.MEMORY_FORGET_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.MEMORY_FORGET)
    @PostMapping("/forget")
    public ResponseEntity<MemoryMutationResultDTO> forget(@Valid @RequestBody ForgetMemoryCommand request) {
        return noStore(memory.replayCommittedForget(context(TianjiAgentPermissions.MEMORY_FORGET_API), request));
    }

    private MemoryOperationContext context(String resource) {
        var current = access.require(resource); Instant maximum = Instant.now().plusSeconds(30);
        return new MemoryOperationContext(current.actor(), current.expiresAt().isBefore(maximum) ? current.expiresAt() : maximum, null);
    }
    private static <T> ResponseEntity<T> noStore(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
