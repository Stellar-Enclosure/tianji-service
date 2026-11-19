package top.egon.cola.platform.tianji.agent.server.integration.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.McpToolMaterialVO;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpTransportBindingRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpTransportBindingVO;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Fixed host metadata only; a matching mapping or fingerprint is not execution authority. */
public final class TianjiFixedReadOnlyMcpToolBindings {
    public static final String TRANSPORT_MODE = "YUHENG_STABLE_JSON_POST";
    public static final String PROTOCOL_VERSION = "2025-11-25";
    public static final int MAX_RESPONSE_BYTES = 1_048_576;
    public static final int MAX_REMOTE_TOOL_DOCUMENT_BYTES = 131_072;
    public static final int MAX_JSON_DEPTH = 8;
    public static final int MAX_JSON_NODES = 4096;
    public static final int MAX_JSON_CONTAINER_SIZE = 256;
    public static final int MAX_JSON_NUMBER_BITS = 512;
    public static final int MAX_JSON_NUMBER_PRECISION = 128;
    public static final int MAX_JSON_NUMBER_SCALE = 128;
    private static final Set<String> IDENTITY_SCOPES = Set.of("openid", "profile", "email", "phone", "address", "offline_access");
    private static final String DNS_LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final Pattern SERVICE_DNS = Pattern.compile(DNS_LABEL + "\\." + DNS_LABEL + "\\.svc\\.cluster\\.local");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> SCHEMA = new TypeReference<>() { };
    private final Map<List<Object>, ResolvedBindingVO> routes;
    private final List<BindingVO> bindings;
    private final boolean requirePublication;

    public TianjiFixedReadOnlyMcpToolBindings(Collection<ToolDescriptor> descriptors, Collection<BindingVO> bindings) {
        this(descriptors, bindings, null);
    }

    public TianjiFixedReadOnlyMcpToolBindings(Collection<ToolDescriptor> descriptors, Collection<BindingVO> bindings,
            @Nullable IdpTransportBindingRegistry transportBindings) {
        if (descriptors == null || descriptors.isEmpty() || descriptors.size() > 256 || bindings == null || bindings.isEmpty() || bindings.size() > 256) throw invalid();
        var registered = new HashMap<String, ToolDescriptor>();
        for (var descriptor : descriptors) if (descriptor == null || registered.putIfAbsent(descriptor.versionKey(), descriptor) != null) throw invalid();
        var configured = new ArrayList<>(bindings); var routes = new HashMap<List<Object>, ResolvedBindingVO>(); var definitionHashes = new HashMap<List<Object>, String>();
        for (var binding : configured) {
            if (binding == null) throw invalid();
            if (transportBindings != null) binding.backend().requireApprovedTransport(transportBindings);
            var descriptor = registered.get(binding.tool().key() + '@' + binding.tool().version());
            var resolved = new ResolvedBindingVO(descriptor, binding);
            var origin = List.<Object>of(binding.tenantId(), binding.ownerIssuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion());
            var slot = new ArrayList<>(origin); slot.add(binding.tool().key()); slot.add(binding.tool().version());
            String previous = definitionHashes.putIfAbsent(origin, binding.definitionContentSha256());
            if (routes.putIfAbsent(List.copyOf(slot), resolved) != null || previous != null && !previous.equals(binding.definitionContentSha256())) throw invalid();
        }
        this.routes = Map.copyOf(routes); this.bindings = List.copyOf(configured);
        requirePublication = transportBindings != null;
    }

    public ResolvedBindingVO resolve(ReadOnlyMcpToolTransport.Request request) {
        try {
            if (request == null) throw unavailable(); var run = request.run(); var actor = run.actor(); var descriptor = request.descriptor();
            if ("LEGACY".equals(actor.issuerFingerprint())) throw unavailable();
            var slot = List.<Object>of(actor.tenantId(), actor.issuerFingerprint(), actor.subjectType(), run.definitionKey(), run.definitionVersion(), descriptor.key(), descriptor.version());
            var selected = routes.get(slot);
            if (selected == null || !selected.binding().definitionContentSha256().equals(run.definitionSha256())
                    || !selected.binding().tool().equals(descriptor.reference()) || !selected.descriptor().equals(descriptor)
                    || !selected.binding().backend().bindingSha256().equals(descriptor.policy().bindingSha256())) throw unavailable();
            if (requirePublication || request.siMaterial() != null) {
                selected.binding().backend().requirePublication(request.siMaterial());
            }
            return selected;
        } catch (RuntimeException uncertain) { throw unavailable(); }
    }
    public List<BindingVO> bindings() { return bindings; }

    /** Pins the complete JSON document; it does not authenticate a server or attest JSON Schema semantics. */
    public static String remoteToolDocumentSha256(Map<String, Object> document) {
        try {
            if (document == null) throw invalid();
            Object canonical = canonical(document, 1, new NodeCounter());
            byte[] serialized = JSON.writeValueAsBytes(canonical);
            if (serialized.length > MAX_REMOTE_TOOL_DOCUMENT_BYTES) throw invalid();
            return OrchestrationRequestDigests.hash(List.of("TIANJI_MCP_REMOTE_TOOL_V1", new String(serialized, StandardCharsets.UTF_8)), Map.of());
        } catch (JsonProcessingException | RuntimeException malformed) { throw invalid(); }
    }

    public record BackendVO(long tenantId, URI issuer, URI endpoint, URI resourceUri, String registrationId,
                            String expectedClientId, String expectedAppId, String expectedSourceBiz, String expectedSourceApp,
                            String expectedEnvironment, Set<String> businessScopes, String expectedServerName,
                            String expectedServerVersion, String remoteToolName, String remoteToolDocumentSha256,
                            ToolSchema inputSchema, ToolSchema outputSchema, Boolean nonBillable, Duration timeout,
                            @Nullable McpPublicationPinVO publication) {
        public BackendVO(long tenantId, URI issuer, URI endpoint, URI resourceUri, String registrationId,
                         String expectedClientId, String expectedAppId, String expectedSourceBiz, String expectedSourceApp,
                         String expectedEnvironment, Set<String> businessScopes, String expectedServerName,
                         String expectedServerVersion, String remoteToolName, String remoteToolDocumentSha256,
                         ToolSchema inputSchema, ToolSchema outputSchema, Boolean nonBillable, Duration timeout) {
            this(tenantId, issuer, endpoint, resourceUri, registrationId, expectedClientId, expectedAppId,
                    expectedSourceBiz, expectedSourceApp, expectedEnvironment, businessScopes, expectedServerName,
                    expectedServerVersion, remoteToolName, remoteToolDocumentSha256, inputSchema, outputSchema,
                    nonBillable, timeout, null);
        }

        @ConstructorBinding
        public BackendVO {
            try {
                if (tenantId < 1 || !text(registrationId, 128) || !text(expectedClientId, 128) || !text(expectedAppId, 128)
                        || !text(expectedSourceBiz, 128) || !text(expectedSourceApp, 128) || !text(expectedEnvironment, 128)
                        || !text(expectedServerName, 128) || !text(expectedServerVersion, 128) || remoteToolName == null || !remoteToolName.matches("[A-Za-z0-9_.-]{1,128}")
                        || !hash(remoteToolDocumentSha256) || inputSchema == null || outputSchema == null || !Boolean.TRUE.equals(nonBillable)
                        || businessScopes == null || businessScopes.isEmpty() || businessScopes.size() > 64 || timeout == null
                        || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0) throw invalid();
                issuer = secure(issuer, 2048, false);
                endpoint = isInternalService(endpoint) ? endpoint : secure(endpoint, 2048, true);
                resourceUri = secure(resourceUri, 512, false);
                if (publication != null && !isInternalService(endpoint)) throw invalid();
                for (var scope : businessScopes) if (scope == null || !scope.matches("[A-Za-z0-9._:-]{1,128}") || IDENTITY_SCOPES.contains(scope)) throw invalid();
                businessScopes = Set.copyOf(businessScopes);
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        public String bindingSha256() {
            var scopes = new HashMap<String, Object>(); businessScopes.forEach(scope -> scopes.put(scope, true));
            String backendDigest = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_MCP_READ_ONLY_BACKEND_V1", Long.toString(tenantId), issuer.toASCIIString(), endpoint.toASCIIString(), resourceUri.toASCIIString(),
                    registrationId, expectedClientId, expectedAppId, expectedSourceBiz, expectedSourceApp, expectedEnvironment, expectedServerName, expectedServerVersion, remoteToolName, remoteToolDocumentSha256,
                    schemaSha256(inputSchema), schemaSha256(outputSchema), nonBillable.toString(), timeout.toString(), TRANSPORT_MODE, PROTOCOL_VERSION, Integer.toString(MAX_RESPONSE_BYTES),
                    Integer.toString(MAX_REMOTE_TOOL_DOCUMENT_BYTES), Integer.toString(MAX_JSON_DEPTH), Integer.toString(MAX_JSON_NODES), Integer.toString(MAX_JSON_CONTAINER_SIZE),
                    Integer.toString(MAX_JSON_NUMBER_BITS), Integer.toString(MAX_JSON_NUMBER_PRECISION), Integer.toString(MAX_JSON_NUMBER_SCALE)), scopes);
            if (publication == null) return backendDigest;
            return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_MCP_READ_ONLY_BACKEND_V2",
                    backendDigest, publication.bindingSha256()), Map.of());
        }
        private void requireApprovedTransport(IdpTransportBindingRegistry registry) {
            if (publication == null) throw invalid();
            var post = requireRoute(registry, publication.postBindingId(), "POST");
            var delete = requireRoute(registry, publication.deleteBindingId(), "DELETE");
            if (!sameServiceEndpoint(post.physicalUri(), endpoint) || !sameServiceEndpoint(delete.physicalUri(), endpoint)) {
                throw invalid();
            }
        }
        private IdpTransportBindingVO requireRoute(IdpTransportBindingRegistry registry, String bindingId, String method) {
            var route = registry.require(bindingId, publication.bindingVersion(), issuer, publication.canonicalEndpoint(),
                    resourceUri, registrationId, IdpTransportBindingVO.Operation.RESOURCE, method);
            if (route.transport() != IdpTransportBindingVO.Transport.HTTP) throw invalid();
            return route;
        }
        private void requirePublication(@Nullable McpToolMaterialVO material) {
            if (publication == null || material == null || tenantId != material.identityTenantId()
                    || !publication.matches(material) || !remoteToolName.equals(material.toolName())) throw unavailable();

            var body = readPublication(material.publication().specJson());
            String source = body.path("sourceType").asText();
            var service = body.path("serviceBinding");
            if (!"mcp-server-v2".equals(body.path("schemaVersion").textValue())
                    || !"STREAMABLE_HTTP".equals(body.path("transport").textValue())
                    || !PROTOCOL_VERSION.equals(body.path("protocolVersion").textValue())
                    || !"INTERNAL_SERVICE".equals(source) && !"COMPOSED_API".equals(source)
                    || "COMPOSED_API".equals(source) && publication.catalogueFileSha256() == null
                    || !endpoint.toASCIIString().equals(body.path("endpoint").textValue())
                    || !publication.postBindingId().equals(service.path("bindingId").textValue())
                    || !Long.toString(publication.bindingVersion()).equals(service.path("revision").textValue())
                    || !endpoint.getHost().equals(service.path("serviceName").asText() + "."
                            + service.path("namespace").asText() + ".svc.cluster.local")
                    || !service.path("port").isIntegralNumber() || !service.path("port").canConvertToInt()
                    || effectivePort(endpoint) != service.path("port").intValue()) throw unavailable();

            var tools = body.path("tools");
            if (!tools.isArray()) throw unavailable();
            JsonNode selected = null;
            for (var tool : tools) {
                if (material.toolId().equals(tool.path("toolId").textValue())) {
                    if (selected != null || !material.toolName().equals(tool.path("name").textValue())
                            || !tool.path("enabled").isBoolean() || !tool.path("enabled").booleanValue()) {
                        throw unavailable();
                    }
                    selected = tool;
                }
            }
            if (selected == null) throw unavailable();
            TianjiMcpJsonRpcTransport.schema(JSON.convertValue(selected.get("inputSchema"), SCHEMA), inputSchema);
            TianjiMcpJsonRpcTransport.schema(JSON.convertValue(selected.get("outputSchema"), SCHEMA), outputSchema);
        }
        @Override public boolean equals(Object value) {
            if (this == value) return true; if (!(value instanceof BackendVO other)) return false;
            return tenantId == other.tenantId && issuer.toASCIIString().equals(other.issuer.toASCIIString()) && endpoint.toASCIIString().equals(other.endpoint.toASCIIString())
                    && resourceUri.toASCIIString().equals(other.resourceUri.toASCIIString()) && registrationId.equals(other.registrationId) && expectedClientId.equals(other.expectedClientId)
                    && expectedAppId.equals(other.expectedAppId) && expectedSourceBiz.equals(other.expectedSourceBiz) && expectedSourceApp.equals(other.expectedSourceApp)
                    && expectedEnvironment.equals(other.expectedEnvironment) && businessScopes.equals(other.businessScopes) && expectedServerName.equals(other.expectedServerName)
                    && expectedServerVersion.equals(other.expectedServerVersion) && remoteToolName.equals(other.remoteToolName) && remoteToolDocumentSha256.equals(other.remoteToolDocumentSha256)
                    && inputSchema.equals(other.inputSchema) && outputSchema.equals(other.outputSchema) && nonBillable.equals(other.nonBillable)
                    && timeout.equals(other.timeout) && Objects.equals(publication, other.publication);
        }
        @Override public int hashCode() {
            return Objects.hash(tenantId, issuer.toASCIIString(), endpoint.toASCIIString(), resourceUri.toASCIIString(), registrationId, expectedClientId, expectedAppId,
                    expectedSourceBiz, expectedSourceApp, expectedEnvironment, businessScopes, expectedServerName, expectedServerVersion, remoteToolName, remoteToolDocumentSha256,
                    inputSchema, outputSchema, nonBillable, timeout, publication);
        }
        @Override public String toString() { return "TianjiFixedReadOnlyMcpToolBindings.BackendVO[redacted]"; }
    }

    /** Reviewed deployment link to the authority's immutable SI release and tool. */
    public record McpPublicationPinVO(VersionIdentityVO identity, String bizCode, String namespaceCode,
            String callerAppCode, @Nullable String ownerAppCode, String resourceKey, String toolId,
            URI canonicalEndpoint, String postBindingId, String deleteBindingId, long bindingVersion,
            @Nullable String catalogueFileSha256) {
        public McpPublicationPinVO(VersionIdentityVO identity, String bizCode, String namespaceCode,
                String callerAppCode, @Nullable String ownerAppCode, String resourceKey, String toolId,
                URI canonicalEndpoint, String postBindingId, String deleteBindingId, long bindingVersion) {
            this(identity, bizCode, namespaceCode, callerAppCode, ownerAppCode, resourceKey, toolId,
                    canonicalEndpoint, postBindingId, deleteBindingId, bindingVersion, null);
        }

        @ConstructorBinding
        public McpPublicationPinVO {
            if (identity == null || identity.kind() != DdcSiKindEnum.MCP
                    || !coordinate(bizCode) || !coordinate(namespaceCode) || !coordinate(callerAppCode)
                    || ownerAppCode != null && !coordinate(ownerAppCode)
                    || !coordinate(resourceKey) || !coordinate(toolId) || bindingVersion < 1
                    || !isDnsLabel(postBindingId) || !isDnsLabel(deleteBindingId) || postBindingId.equals(deleteBindingId)
                    || catalogueFileSha256 != null && !hash(catalogueFileSha256)) {
                throw invalid();
            }
            canonicalEndpoint = secure(canonicalEndpoint, 2048, false);
        }

        private boolean matches(McpToolMaterialVO material) {
            var scope = material.scope();
            var candidate = material.publication();
            return identity.equals(candidate.identity()) && bizCode.equals(scope.bizCode())
                    && namespaceCode.equals(scope.namespaceCode()) && callerAppCode.equals(scope.callerAppCode())
                    && identity.envCode().equals(scope.envCode()) && Objects.equals(ownerAppCode, candidate.ownerAppCode())
                    && resourceKey.equals(candidate.key()) && toolId.equals(material.toolId());
        }

        private String bindingSha256() {
            return OrchestrationRequestDigests.hash(List.of("TIANJI_MCP_SI_PUBLICATION_V1", identity.tenantId(),
                    identity.resourceId(), identity.versionId(), identity.versionNo(), identity.kind().name(),
                    identity.envCode(), identity.digestSha256(), bizCode, namespaceCode, callerAppCode,
                    Objects.toString(ownerAppCode, ""), resourceKey, toolId, canonicalEndpoint.toASCIIString(),
                    postBindingId, deleteBindingId, Long.toString(bindingVersion),
                    Objects.toString(catalogueFileSha256, "")), Map.of());
        }

        @Override public String toString() { return "TianjiFixedReadOnlyMcpToolBindings.McpPublicationPinVO[redacted]"; }
    }
    public record BindingVO(long tenantId, String ownerIssuerFingerprint, TrustedAgentActor.SubjectType principalType,
                            String definitionKey, long definitionVersion, String definitionContentSha256,
                            DefinitionResourceRef tool, BackendVO backend) {
        public BindingVO {
            if (tenantId < 1 || !hash(ownerIssuerFingerprint) || principalType == null || !text(definitionKey, 128) || definitionVersion < 1 || !hash(definitionContentSha256)
                    || tool == null || !key(tool.key()) || backend == null || tenantId != backend.tenantId()) throw invalid();
        }
        @Override public String toString() { return "TianjiFixedReadOnlyMcpToolBindings.BindingVO[redacted]"; }
    }
    public record ResolvedBindingVO(ToolDescriptor descriptor, BindingVO binding) {
        public ResolvedBindingVO {
            if (descriptor == null || binding == null || !descriptor.reference().equals(binding.tool()) || descriptor.policy().risk() != ToolPolicy.Risk.READ_ONLY
                    || !ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey())
                    || !binding.backend().bindingSha256().equals(descriptor.policy().bindingSha256())
                    || !descriptor.input().equals(binding.backend().inputSchema()) || !descriptor.output().equals(binding.backend().outputSchema())) throw invalid();
        }
        @Override public String toString() { return "TianjiFixedReadOnlyMcpToolBindings.ResolvedBindingVO[redacted]"; }
    }

    private static String schemaSha256(ToolSchema schema) {
        var values = new HashMap<String, Object>();
        schema.fields().forEach((field, type) -> { values.put(field + ".type", type.name()); values.put(field + ".required", schema.required().contains(field)); });
        return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_MCP_SCHEMA_V1"), values);
    }
    private static Object canonical(Object value, int depth, NodeCounter counter) {
        if (depth > MAX_JSON_DEPTH) throw invalid(); counter.node();
        if (value == null || value instanceof Boolean) return value;
        if (value instanceof String text) { counter.text(text); return text; }
        if (value instanceof Map<?, ?> object) {
            if (object.size() > MAX_JSON_CONTAINER_SIZE) throw invalid();
            var result = new TreeMap<String, Object>(); int entries = 0;
            for (var entry : object.entrySet()) {
                if (++entries > MAX_JSON_CONTAINER_SIZE || !(entry.getKey() instanceof String key)) throw invalid();
                counter.node(); counter.text(key); result.put(key, canonical(entry.getValue(), depth + 1, counter));
            }
            return result;
        }
        if (value instanceof List<?> array) {
            if (array.size() > MAX_JSON_CONTAINER_SIZE) throw invalid();
            var result = new ArrayList<Object>(); int entries = 0;
            for (var element : array) { if (++entries > MAX_JSON_CONTAINER_SIZE) throw invalid(); result.add(canonical(element, depth + 1, counter)); }
            return result;
        }
        return canonicalNumber(value);
    }
    private static BigDecimal canonicalNumber(Object value) {
        BigDecimal decimal; Class<?> type = value.getClass();
        if (type == Byte.class || type == Short.class || type == Integer.class || type == Long.class) decimal = BigDecimal.valueOf(((Number) value).longValue());
        else if (type == BigInteger.class) {
            var integer = (BigInteger) value; if (integer.abs().bitLength() > MAX_JSON_NUMBER_BITS) throw invalid(); decimal = new BigDecimal(integer);
        } else if (type == BigDecimal.class) {
            decimal = (BigDecimal) value; if (decimal.unscaledValue().abs().bitLength() > MAX_JSON_NUMBER_BITS) throw invalid();
        } else if (type == Float.class) {
            float number = (Float) value; if (!Float.isFinite(number)) throw invalid(); decimal = new BigDecimal(Float.toString(number));
        } else if (type == Double.class) {
            double number = (Double) value; if (!Double.isFinite(number)) throw invalid(); decimal = new BigDecimal(Double.toString(number));
        } else throw invalid();
        decimal = decimal.stripTrailingZeros();
        if (decimal.precision() > MAX_JSON_NUMBER_PRECISION || decimal.scale() < -MAX_JSON_NUMBER_SCALE || decimal.scale() > MAX_JSON_NUMBER_SCALE) throw invalid();
        return decimal;
    }
    private static final class NodeCounter {
        private int nodes;
        private int textBytes;
        private void node() { if (++nodes > MAX_JSON_NODES) throw invalid(); }
        private void text(String value) {
            if (value.length() > MAX_REMOTE_TOOL_DOCUMENT_BYTES || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_REMOTE_TOOL_DOCUMENT_BYTES - textBytes) throw invalid(); textBytes += bytes;
        }
    }
    private static URI secure(URI value, int maximum, boolean loopbackEndpoint) {
        if (value == null || value.getHost() == null || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !value.toASCIIString().equals(value.toString()) || !value.normalize().toASCIIString().equals(value.toASCIIString()) || value.toASCIIString().length() > maximum) throw invalid();
        if (!"https".equalsIgnoreCase(value.getScheme()) && !(loopbackEndpoint && "http".equalsIgnoreCase(value.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(value.getHost().toLowerCase(Locale.ROOT)))) throw invalid();
        return value;
    }
    static boolean isInternalService(URI endpoint) {
        if (endpoint == null || !"http".equals(endpoint.getScheme()) || endpoint.getHost() == null
                || !SERVICE_DNS.matcher(endpoint.getHost()).matches() || endpoint.getRawUserInfo() != null
                || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                || endpoint.getPort() == 0 || endpoint.getPort() > 65535) {
            return false;
        }
        String path = endpoint.getRawPath();
        String authority = endpoint.getHost() + (endpoint.getPort() < 0 ? "" : ":" + endpoint.getPort());
        return authority.equals(endpoint.getRawAuthority())
                && path != null && path.startsWith("/") && path.length() <= 1024 && !path.contains("//")
                && !path.contains("%") && path.chars().allMatch(value -> value >= 0x21 && value <= 0x7e)
                && endpoint.normalize().toASCIIString().equals(endpoint.toASCIIString());
    }
    private static boolean sameServiceEndpoint(URI expected, URI actual) {
        return isInternalService(actual) && expected.getHost().equals(actual.getHost())
                && effectivePort(expected) == effectivePort(actual)
                && expected.getRawPath().equals(actual.getRawPath());
    }
    private static int effectivePort(URI endpoint) { return endpoint.getPort() < 0 ? 80 : endpoint.getPort(); }
    private static boolean isDnsLabel(String label) { return label != null && label.matches(DNS_LABEL); }
    private static boolean coordinate(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    }
    private static JsonNode readPublication(String body) {
        try { return JSON.readTree(body); }
        catch (JsonProcessingException invalid) { throw unavailable(); }
    }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean key(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}"); }
    private static boolean text(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANJI_FIXED_READ_ONLY_MCP_TOOL_BINDING_INVALID"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("TIANJI_FIXED_READ_ONLY_MCP_TOOL_BINDING_UNAVAILABLE"); }
}
