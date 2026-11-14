package top.egon.cola.platform.tianji.process.server.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService.AutomationObservationPermitVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionConverter;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionStore.NodePageKeyVO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess.AutomationInstanceAccessVO;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest.PrincipalType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.RequiresPermissions;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.AUTOMATION_NODE_EXECUTION_LIST_API;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.OBSERVE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.OBSERVE_SCOPE;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.READ;
import static top.egon.cola.platform.tianji.process.server.security.TianjiProcessPermissions.READ_SCOPE;

@Slf4j
@Validated
@RestController("processNodeExecutionController")
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessNodeExecutionController {
    public static final String PATH = ProcessAutomationController.INSTANCE_PATH + "/node-executions";
    private static final String INSTANCE_ID = "[^\\p{Cc}]{1,255}";
    private static final String NODE_ID = "[A-Za-z][A-Za-z0-9_.:-]{0,254}";
    private static final String OCCURRENCE_ID = "[A-Za-z][A-Za-z0-9_.:-]{0,127}";
    private static final String CURSOR = "[A-Za-z0-9_-]{1,2048}";
    private static final Set<String> PARAMS = Set.of("nodeId", "limit", "cursor");
    private static final JsonMapper CURSOR_JSON = JsonMapper.builder().build();

    @Qualifier("tianjiProcessAccess") private final TianjiProcessAccess access;
    @Qualifier("processExecutionService") private final ProcessExecutionService instances;
    @Qualifier("processInstanceBindingStore") private final ProcessInstanceBindingStore bindings;
    @Qualifier("processNodeExecutionStore") private final ProcessNodeExecutionStore nodes;
    @Qualifier("processNodeExecutionConverter") private final ProcessNodeExecutionConverter converter;
    @Qualifier("processDraftTransactionTemplate") private final TransactionTemplate transaction;

    @GetMapping(PATH)
    @PermissionResource(key = AUTOMATION_NODE_EXECUTION_LIST_API, type = ResourceType.API, labelPage = "tianji.process.permissions",
            labelKey = "api.automation.node.execution.list", allowedPrincipals = {PrincipalType.USER, PrincipalType.SERVICE},
            oauthScopes = {READ_SCOPE, OBSERVE_SCOPE})
    @RequiresPermissions(mode = PermissionMode.ANY, codes = {READ, OBSERVE})
    public ResponseEntity<ProcessNodeExecutionPageDTO> listProcessNodeExecutions(
            @PathVariable("instanceId") @Pattern(regexp = INSTANCE_ID) String instanceId, HttpServletRequest request) {
        var query = query(instanceId, request);
        var observation = access.observeAutomationNodes();
        if (!observation.permit().ownerRead() && !observation.permit().observe()) {
            throw new AccessDeniedException("Current node read or observation permission is required");
        }
        var permit = observation.permit();
        var metadata = instances.observeAutomation(observation.actor(), instanceId,
                new AutomationObservationPermitVO(permit.ownerRead(), permit.observe(), false, false, false));
        var read = new TransactionTemplate(transaction.getTransactionManager());
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setReadOnly(true);
        read.setTimeout(transaction.getTimeout());
        var page = Objects.requireNonNull(read.execute(status -> page(observation, instanceId, query, metadata)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
    }

    private ProcessNodeExecutionPageDTO page(AutomationInstanceAccessVO observation, String instanceId, QueryVO query,
                                             AutomationInstanceDTO metadata) {
        var actor = observation.actor();
        var permit = observation.permit();
        var binding = bindings.find(actor.tenantId(), instanceId).orElseThrow(ProcessResourceNotFoundException::new);
        boolean owner = actor.equals(binding.getOwner());
        if (!permit.observe() && !owner) throw new ProcessResourceNotFoundException();
        if (metadata == null || metadata.getProfile() != AutomationInstanceDTO.ProfileEnum.AUTOMATION_V2
                || binding.getTenantId() != actor.tenantId() || !instanceId.equals(binding.getInstanceId())
                || !instanceId.equals(metadata.getInstanceId()) || !binding.getReleaseKey().equals(metadata.getReleaseVersion())
                || !binding.getDefinitionKey().equals(metadata.getDefinitionKey())
                || binding.getActivationRevision() != metadata.getActivationRevision()
                || !binding.getConfigurationSha256().equals(metadata.getConfigurationSha256())) {
            throw unavailable();
        }
        var observedAt = nodes.now();
        var scopeHash = cursorScope(actor, instanceId, binding.getReleaseKey(), query.nodeId());
        var cursor = query.cursor() == null ? null : decodeCursor(query.cursor(), scopeHash, observedAt);
        var highWater = cursor == null ? observedAt : cursor.highWater();
        var rows = nodes.list(actor.tenantId(), instanceId, query.nodeId(), highWater,
                cursor == null ? null : cursor.key(), query.limit() + 1);
        verifyRows(rows, actor.tenantId(), instanceId, binding.getConfigurationSha256(), query, highWater,
                cursor == null ? null : cursor.key());
        boolean data = permit.dataRead() || permit.dataReadOwnerOnly() && owner;
        var items = rows.stream().limit(query.limit())
                .map(row -> converter.toTarget(row, data, permit.diagnosticRead())).toList();
        String next = rows.size() <= query.limit() ? null
                : encodeCursor(scopeHash, new CursorVO(highWater, key(rows.get(query.limit() - 1))));
        return new ProcessNodeExecutionPageDTO(instanceId, binding.getReleaseKey(), observedAt, items, next);
    }

    private static NodePageKeyVO key(ProcessNodeExecutionBO row) {
        return new NodePageKeyVO(row.getStartedAt(), row.getOccurrenceId(), row.getAttempt());
    }

    private static void verifyRows(List<ProcessNodeExecutionBO> rows, long tenantId, String instanceId, String configurationSha256,
                                   QueryVO query, Instant highWater, NodePageKeyVO after) {
        if (rows.size() > query.limit() + 1) throw unavailable();
        var keys = new HashSet<NodePageKeyVO>();
        var references = new HashSet<String>();
        Instant previous = after == null ? highWater : after.startedAt();
        for (var row : rows) {
            var key = key(row);
            if (row.getTenantId() != tenantId || !instanceId.equals(row.getInstanceId())
                    || !configurationSha256.equals(row.getConfigurationSha256())
                    || query.nodeId() != null && !query.nodeId().equals(row.getNodeId()) || key.startedAt().isAfter(previous)
                    || key.equals(after) || !keys.add(key) || !references.add(row.getNodeExecutionRef())) throw unavailable();
            previous = key.startedAt();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Automation node source evidence is unavailable or inconsistent");
    }

    private static QueryVO query(String instanceId, HttpServletRequest request) {
        if (instanceId.length() > 255 || instanceId.isBlank() || !instanceId.matches(INSTANCE_ID)) throw invalid();
        try {
            if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) throw invalid();
        } catch (IOException unreadable) {
            throw invalid();
        }
        var values = request.getParameterMap();
        if (!PARAMS.containsAll(values.keySet()) || values.values().stream().anyMatch(value -> value.length != 1)) throw invalid();
        String nodeId = parameter(values, "nodeId", NODE_ID);
        String limit = parameter(values, "limit", "(?:[1-9][0-9]?|100)");
        return new QueryVO(nodeId, limit == null ? 20 : Integer.parseInt(limit), parameter(values, "cursor", CURSOR));
    }

    private static String parameter(Map<String, String[]> parameters, String name, String pattern) {
        if (!parameters.containsKey(name)) return null;
        String value = parameters.get(name)[0];
        if (!value.matches(pattern)) throw invalid();
        return value;
    }

    private static String cursorScope(ProcessActor actor, String instanceId, String release, String nodeId) {
        var scope = new LinkedHashMap<String, Object>();
        scope.put("tenantId", Long.toString(actor.tenantId()));
        scope.put("actorType", actor.subjectType().name());
        scope.put("actorIssuerSha256", actor.issuerFingerprint());
        scope.put("actorSubject", actor.subjectId());
        scope.put("instanceId", instanceId);
        scope.put("releaseVersion", release);
        scope.put("nodeId", nodeId);
        return ProcessTypedJson.sha256("AUTOMATION_NODE_EXECUTION_CURSOR_SCOPE_V2", ProcessTypedJson.canonicalBytes(scope));
    }

    private static String encodeCursor(String scopeHash, CursorVO cursor) {
        var key = cursor.key();
        byte[] wire = ProcessTypedJson.canonicalBytes(Map.of("scopeSha256", scopeHash, "highWater", cursor.highWater().toString(),
                "startedAt", key.startedAt().toString(), "occurrenceId", key.occurrenceId(), "attempt", key.attempt()));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(wire);
    }

    private static CursorVO decodeCursor(String token, String scopeHash, Instant observedAt) {
        try {
            var root = CURSOR_JSON.readTree(Base64.getUrlDecoder().decode(token));
            var highWater = Instant.parse(root.path("highWater").asString());
            var startedAt = Instant.parse(root.path("startedAt").asString());
            String occurrence = root.path("occurrenceId").asString();
            int attempt = root.path("attempt").asInt();
            if (highWater.getNano() % 1000 != 0 || startedAt.getNano() % 1000 != 0 || highWater.isAfter(observedAt)
                    || startedAt.isAfter(highWater) || !occurrence.matches(OCCURRENCE_ID) || attempt < 1 || attempt > 10) throw invalid();
            var cursor = new CursorVO(highWater, new NodePageKeyVO(startedAt, occurrence, attempt));
            if (!token.equals(encodeCursor(scopeHash, cursor))) throw invalid();
            return cursor;
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid automation node observation");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<OrchestrationErrorDTO> invalidInput() {
        return ProcessApiErrors.error(HttpStatus.BAD_REQUEST, "TIANJI_INVALID_COMMAND");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<OrchestrationErrorDTO> invalidMethod(HandlerMethodValidationException failure) {
        return failure.isForReturnValue()
                ? ProcessApiErrors.error(HttpStatus.INTERNAL_SERVER_ERROR, "TIANJI_INTERNAL_ERROR")
                : invalidInput();
    }

    private record QueryVO(String nodeId, int limit, String cursor) { }
    private record CursorVO(Instant highWater, NodePageKeyVO key) { }
}
