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
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactVerifier;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiAgentResultArtifactConfiguration;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentPermissions;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Slf4j
@RestController("agentRuntimeTemplateController")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.api", name = "enabled", havingValue = "true")
public class AgentRuntimeTemplateController {
    public static final String PATH = "/api/tianji/v2/agent-definitions";
    static final String NEW_ADMISSION = "stellar.tianji.agent.runtime-v2.enabled";

    private static final ProcessSchemaVO ARTIFACT_SCHEMA = ProcessSchemaVO.fromWire(Map.of(
            "type", "object", "additionalProperties", false,
            "properties", Map.of(
                    "resourceKey", Map.of("type", "string", "maxLength", 128, "pattern", "^[A-Za-z][A-Za-z0-9_.:-]{0,127}$"),
                    "contentSha256", Map.of("type", "string", "maxLength", 64, "pattern", "^[0-9a-f]{64}$"),
                    "mediaType", Map.of("type", "string", "maxLength", 127, "pattern", "^[a-z0-9.+-]{1,63}/[a-z0-9.+-]{1,63}$"),
                    "sizeBytes", Map.of("type", "integer", "minimum", 0, "maximum", ProcessTypedJson.V2_MAX_WIRE_INTEGER)),
            "required", List.of("resourceKey", "contentSha256", "mediaType", "sizeBytes")));

    private final AgentLedgerService ledger;
    @Qualifier("tianjiAgentAccess")
    private final TianjiAgentAccess access;
    @Qualifier("environment")
    private final Environment environment;
    @Qualifier("tianjiAgentRuntimeTemplatePublicationAuthority")
    private final ObjectProvider<AgentLedgerService.RuntimeTemplatePublicationAuthority> publication;
    @Qualifier(TianjiAgentResultArtifactConfiguration.VERIFIER)
    private final ObjectProvider<AgentResultArtifactVerifier> artifactVerifiers;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AgentErrorDTO> invalidCommand() {
        return invalidRuntimeCommandResponse();
    }

    static ResponseEntity<AgentErrorDTO> invalidRuntimeCommandResponse() {
        var code = AgentErrorCodeEnum.TIANJI_INVALID_COMMAND;
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(new AgentErrorDTO(code, code.name(), null, null, null));
    }

    @PermissionResource(key = TianjiAgentPermissions.TEMPLATE_PUBLISH_API, type = ResourceType.API,
            labelPage = "tianji.permissions", labelKey = "api.agent.templatePublish",
            allowedPrincipals = PermissionManifest.PrincipalType.SERVICE, oauthScopes = TianjiAgentPermissions.TEMPLATE_PUBLISH_SCOPE)
    @RequiresPermissions(codes = TianjiAgentPermissions.TEMPLATE_PUBLISH)
    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentRuntimeTemplateDTO> publish(@Valid @RequestBody AgentRuntimeTemplateCommand command) {
        var publisher = access.require(TianjiAgentPermissions.TEMPLATE_PUBLISH_API);
        if (publisher.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent publication service required");
        }
        try {
            var receipt = ledger.publishRuntimeTemplate(publisher.actor(), command,
                    () -> environment.getProperty(NEW_ADMISSION, Boolean.class, false),
                    (actor, template) -> publicationProof(publisher, actor, template));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt);
        } catch (AgentLedgerException known) {
            if (known.code() == AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE) throw unavailable();
            throw known;
        }
    }

    private Predicate<Instant> publicationProof(TianjiAgentAccess.Authorized publisher, TrustedAgentActor actor,
            AgentRuntimeTemplateCommand template) {
        var authority = publication.getIfAvailable();
        if (!publisher.actor().equals(actor) || authority == null
                || (isArtifactSchema(template.getOutputSchema()) && artifactVerifiers.getIfAvailable() == null)) throw unavailable();
        var proof = authority.verify(actor, template);
        if (proof == null) throw unavailable();
        return databaseNow -> publisher.expiresAt().isAfter(databaseNow) && proof.test(databaseNow);
    }

    private static boolean isArtifactSchema(ProcessSchemaVO schema) {
        return "object".equals(schema.type()) && Boolean.FALSE.equals(schema.additionalProperties())
                && ARTIFACT_SCHEMA.properties().equals(schema.properties())
                && Set.copyOf(ARTIFACT_SCHEMA.required()).equals(Set.copyOf(schema.required()));
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "Agent runtime template publication is unavailable");
    }
}
