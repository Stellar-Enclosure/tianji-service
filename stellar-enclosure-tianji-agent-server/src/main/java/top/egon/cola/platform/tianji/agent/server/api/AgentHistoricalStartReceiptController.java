package top.egon.cola.platform.tianji.agent.server.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.recovery.TianjiHistoricalStartReceiptConfiguration.HistoricalStartReceiptHost;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServletHistoricalStartReceiptAuthority;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.ENABLED;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.PATH;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.RECONCILE;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.RESOURCE;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.SCOPE;

@RestController("agentHistoricalStartReceiptController")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {"stellar.tianji.agent.api.enabled", "stellar.tianji.agent.storage.enabled", ENABLED}, havingValue = "true")
public class AgentHistoricalStartReceiptController {
    private static final int MAX_BYTES = 512;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final HistoricalStartReceiptHost host;
    private final TianjiServletHistoricalStartReceiptAuthority authority;

    public AgentHistoricalStartReceiptController(
            @Qualifier("tianjiHistoricalStartReceiptHost") HistoricalStartReceiptHost host,
            @Qualifier("tianjiServletHistoricalStartReceiptAuthority") TianjiServletHistoricalStartReceiptAuthority authority) {
        this.host = Objects.requireNonNull(host, "host");
        this.authority = Objects.requireNonNull(authority, "authority");
        if (!host.usesAuthority(authority)) {
            throw new IllegalStateException("Historical START receipt host authority does not match");
        }
    }

    @PermissionResource(key = RESOURCE, type = ResourceType.API, labelPage = "tianji.permissions",
            labelKey = "api.agent.toolReceiptReconcile", allowedPrincipals = PermissionManifest.PrincipalType.USER, oauthScopes = SCOPE)
    @RequiresPermissions(codes = RECONCILE)
    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reconcile(HttpServletRequest request) {
        if (request.getQueryString() != null) throw invalid();
        var command = command(request);
        try {
            var receipt = authority.reconcile(command.runId(), command.intentId(),
                    owner -> host.observe(owner, command.runId(), command.intentId()));
            if (receipt == null || !command.runId().equals(receipt.dispatchRunId()) || !command.intentId().equals(receipt.intentId())) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Historical START receipt result is unavailable");
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(receipt);
        } catch (AgentLedgerException failure) {
            if (failure.code() != AgentErrorCodeEnum.TIANJI_SIDE_EFFECT_UNKNOWN) throw failure;
            return ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore())
                    .body(new AgentErrorDTO(failure.code(), failure.code().name(), null, null, null));
        }
    }

    private static ReconcileStartReceiptCommand command(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_BYTES) throw new AgentRequestBodyAdvice.TooLarge();
        byte[] body;
        try {
            body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        } catch (IOException unreadable) {
            throw invalid();
        }
        if (body.length > MAX_BYTES) throw new AgentRequestBodyAdvice.TooLarge();
        try {
            var json = JSON.readTree(body);
            if (json == null || !json.isObject() || json.size() != 2) throw invalid();
            String runId = text(json.get("runId"));
            if (runId.length() > 64 || !runId.equals(new GetAgentRunQuery(runId).runId())
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(runId)
                    || runId.chars().anyMatch(Character::isISOControl)) throw invalid();
            String intent = text(json.get("intentId"));
            UUID intentId = UUID.fromString(intent);
            if (!intent.equals(intentId.toString())) throw invalid();
            return new ReconcileStartReceiptCommand(runId, intentId);
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static String text(JsonNode value) {
        if (value == null || !value.isString()) throw invalid();
        return value.asString();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Historical START receipt command is invalid");
    }

    private record ReconcileStartReceiptCommand(String runId, UUID intentId) { }
}
