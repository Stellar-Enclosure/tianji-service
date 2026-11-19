package top.egon.cola.platform.tianji.agent.application.run;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.si.TianshuTemplateResolver;
import top.egon.cola.platform.tianji.agent.si.TianshuTemplateResolver.ResolvedAgentExecutionBundle;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateDependencyRefVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongFunction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Prepares exact, authorized SI material before acquiring the linked submission fence. */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TianjiSiExecutionBundleService {
    private static final int MAX_MATERIAL_BYTES = 4 * 1024 * 1024;
    private static final int MAX_RESOURCE_BYTES = 1024 * 1024;
    private static final int MAX_RESOURCES = 257;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> OBJECT_WIRE = new TypeReference<>() { };
    private static final Set<String> BUNDLE_FIELDS =
            Set.of("identityTenantId", "bizCode", "namespaceCode", "envCode", "ownerAppCode", "callerAppCode", "manifest", "resources");
    private static final Set<String> RESOURCE_FIELDS = Set.of("identity", "key", "ownerAppCode", "specJson");

    @Qualifier("tianshuTemplateResolver")
    private final TianshuTemplateResolver resolver;
    @Qualifier("tianjiSiExecutionScope")
    private final ScopeVO scope;
    @Qualifier("tianjiSiExecutionAuthority")
    private final ExecutionAuthority authority;

    @FunctionalInterface
    public interface ExecutionAuthority {
        /** Binds the verified identity tenant to the approved SI target for one synchronous read. */
        ResolvedAgentExecutionBundle read(TrustedAgentActor actor, ScopeVO scope,
                                          LongFunction<ResolvedAgentExecutionBundle> read);
    }

    public FrozenSiBundleVO prepare(@NotNull @Valid TrustedAgentActor actor,
                                     @NotNull @Valid AgentRuntimeTemplateCommand template) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("SI execution preparation requires a transaction-free caller");
        }
        if (!isServiceActor(actor) || template == null || !template.isTemplateConsistent()) throw unavailable();
        try {
            var resolved = authority.read(actor, scope, tenantId -> resolver.resolveBundle(tenantId, scope.bizCode(),
                    scope.namespaceCode(), scope.envCode(), scope.ownerAppCode(), scope.callerAppCode(),
                    template.getTemplateKey(), template.getTemplateVersion()));
            var bundle = new FrozenSiBundleVO(actor.tenantId(), resolved.bizCode(), resolved.namespaceCode(), resolved.envCode(),
                    resolved.ownerAppCode(), resolved.callerAppCode(), resolved.dependencyManifest(),
                    resolved.resources().stream().map(resource -> new SiResourceVO(
                            resource.identity(), resource.key(), resource.ownerAppCode(), resource.specJson())).toList());
            if (!scope.equals(new ScopeVO(bundle.bizCode(), bundle.namespaceCode(), bundle.envCode(),
                    bundle.ownerAppCode(), bundle.callerAppCode()))) {
                throw invalid();
            }
            verifyFrozenBundle(actor, template, bundle);
            return bundle;
        } catch (RuntimeException failure) {
            log.debug("SI execution material preparation failed tenant={} causeType={}",
                    actor.tenantId(), failure.getClass().getSimpleName());
            throw unavailable();
        }
    }

    public static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "SI_PINNED_HISTORY_UNAVAILABLE");
    }

    /** Verifies the run's existing template contract against the immutable authority material without network I/O. */
    public static void verifyFrozenBundle(TrustedAgentActor actor, AgentRuntimeTemplateCommand template,
                                          @Nullable FrozenSiBundleVO bundle) {
        if (!isServiceActor(actor) || bundle == null || template == null || !template.isTemplateConsistent()
                || bundle.identityTenantId() != actor.tenantId()) {
            throw unavailable();
        }
        try {
            verifyTemplateBundle(template, bundle);
        } catch (IllegalArgumentException | NullPointerException malformed) {
            throw unavailable();
        }
    }

    private static void verifyTemplateBundle(AgentRuntimeTemplateCommand template, FrozenSiBundleVO bundle) {
        var root = bundle.root();
        if (!root.key().equals(template.getTemplateKey())
                || !root.identity().versionNo().equals(Long.toString(template.getTemplateVersion()))) throw invalid();
        var configuration = template.getConfiguration();
        verifyAgent(bundle, root.key(), configuration.modelRef(), configuration.instructionRef(), configuration.toolRefs());
        if (configuration.agenticPlan() != null) {
            for (var leaf : configuration.agenticPlan().agents()) {
                verifyAgent(bundle, leaf.agentKey(), leaf.modelRef(), leaf.instructionRef(), leaf.toolRefs());
            }
        }
    }

    private static boolean isServiceActor(@Nullable TrustedAgentActor actor) {
        return actor != null && actor.subjectType() == TrustedAgentActor.SubjectType.SERVICE
                && !"LEGACY".equals(actor.issuerFingerprint());
    }

    private static void verifyAgent(FrozenSiBundleVO bundle, String agentKey, String model,
                                    TemplateDependencyRefVO instruction, List<TemplateDependencyRefVO> tools) {
        var agent = bundle.namedAgent(agentKey);
        if (!model.equals(agent.body().path("modelKey").textValue())) throw invalid();
        bundle.instruction(agentKey, instruction);
        for (var ref : tools) {
            // The SI body's digest covers the fixed MCP publication. Tianji's own descriptor digest
            // is checked independently by the registered tool and execution-grant paths.
            one(bundle.directDependencies(agent, DdcSiKindEnum.MCP).stream()
                    .filter(candidate -> candidate.identity().versionNo().equals(Long.toString(ref.version()))
                            && candidate.declaresTool(ref.key()))
                    .toList());
        }
    }

    public record ScopeVO(String bizCode, String namespaceCode, String envCode, @Nullable String ownerAppCode,
                          String callerAppCode) {
        public ScopeVO {
            coordinate(bizCode);
            coordinate(namespaceCode);
            coordinate(envCode);
            owner(ownerAppCode);
            coordinate(callerAppCode);
        }
    }

    /** Immutable publication evidence; current execution authority is still checked at every tool boundary. */
    public record McpToolMaterialVO(long identityTenantId, ScopeVO scope, String closureDigest,
                                    SiResourceVO publication, String toolId, String toolName) {
        public McpToolMaterialVO {
            if (identityTenantId < 1 || scope == null || closureDigest == null || !closureDigest.matches("[0-9a-f]{64}")
                    || publication == null || publication.identity().kind() != DdcSiKindEnum.MCP
                    || !scope.envCode().equals(publication.identity().envCode())) throw invalid();
            coordinate(toolId);
            coordinate(toolName);
            var declared = declaredTool(publication, toolId);
            if (!toolId.equals(declared.toolId()) || !toolName.equals(declared.name())) throw invalid();
        }

        @Override public String toString() { return "McpToolMaterialVO[redacted]"; }
    }

    public record FrozenSiBundleVO(long identityTenantId, String bizCode, String namespaceCode, String envCode,
            @Nullable String ownerAppCode, String callerAppCode, DdcSiDependencyManifestVO manifest,
            List<SiResourceVO> resources) {
        /** Same-tenant construction for a deployment without a separate identity tenant. */
        public FrozenSiBundleVO(String bizCode, String namespaceCode, String envCode, @Nullable String ownerAppCode,
                                String callerAppCode, DdcSiDependencyManifestVO manifest, List<SiResourceVO> resources) {
            this(Long.parseLong(manifest.root().tenantId()), bizCode, namespaceCode, envCode, ownerAppCode,
                    callerAppCode, manifest, resources);
        }

        public FrozenSiBundleVO {
            new ScopeVO(bizCode, namespaceCode, envCode, ownerAppCode, callerAppCode);
            Objects.requireNonNull(manifest, "SI manifest");
            if (identityTenantId < 1 || resources == null || resources.size() > MAX_RESOURCES
                    || resources.size() != manifest.nodes().size() + 1
                    || manifest.root().kind() != DdcSiKindEnum.AGENT || !envCode.equals(manifest.root().envCode())) {
                throw invalid();
            }
            var expected = new HashSet<>(manifest.nodes());
            expected.add(manifest.root());
            var material = new HashMap<VersionIdentityVO, SiResourceVO>();
            long size = manifest.canonicalBytes().length;
            for (SiResourceVO resource : resources) {
                if (resource == null || !expected.contains(resource.identity())
                        || !envCode.equals(resource.identity().envCode())
                        || material.putIfAbsent(resource.identity(), resource) != null) throw invalid();
                size += resource.specJson().getBytes(StandardCharsets.UTF_8).length;
                if (size > MAX_MATERIAL_BYTES) throw invalid();
            }
            if (!Objects.equals(ownerAppCode, material.get(manifest.root()).ownerAppCode())) throw invalid();
            var ordered = new ArrayList<SiResourceVO>(resources.size());
            ordered.add(material.get(manifest.root()));
            manifest.nodes().forEach(node -> ordered.add(material.get(node)));
            resources = List.copyOf(ordered);
        }

        public String closureDigest() { return manifest.closureDigest(); }

        private SiResourceVO root() { return resources.getFirst(); }

        private SiResourceVO namedAgent(String agentKey) {
            if (root().key().equals(agentKey)) return root();
            return one(resources.stream().filter(resource -> resource.identity().kind() == DdcSiKindEnum.AGENT
                    && resource.key().equals(agentKey)).toList());
        }

        public String instruction(String agentKey, TemplateDependencyRefVO ref) {
            var agent = namedAgent(agentKey);
            String instruction = agent.matches(ref) ? agent.body().path("systemInstruction").textValue()
                    : one(directDependencies(agent, DdcSiKindEnum.PROMPT).stream()
                            .filter(candidate -> candidate.matches(ref)).toList()).body().path("promptText").textValue();
            if (instruction == null || instruction.isBlank()) throw invalid();
            return instruction;
        }

        /** Resolves only the fixed template's exact tool; applicable leaves must select identical publications. */
        public McpToolMaterialVO mcpToolMaterial(AgentRuntimeTemplateCommand template, TemplateDependencyRefVO reference) {
            try {
                if (template == null || !template.isTemplateConsistent() || reference == null) throw invalid();
                verifyTemplateBundle(template, this);
                var configuration = template.getConfiguration();
                var agents = new ArrayList<String>();
                if (configuration.agenticPlan() == null) {
                    if (applicable(configuration.toolRefs(), reference)) agents.add(root().key());
                } else {
                    for (var leaf : configuration.agenticPlan().agents()) {
                        if (applicable(leaf.toolRefs(), reference)) agents.add(leaf.agentKey());
                    }
                }
                if (agents.isEmpty()) throw invalid();
                McpToolMaterialVO selected = null;
                for (String agentKey : agents) {
                    var publication = one(directDependencies(namedAgent(agentKey), DdcSiKindEnum.MCP).stream()
                            .filter(candidate -> candidate.identity().versionNo().equals(Long.toString(reference.version()))
                                    && candidate.declaresTool(reference.key())).toList());
                    var declared = declaredTool(publication, reference.key());
                    var material = new McpToolMaterialVO(identityTenantId,
                            new ScopeVO(bizCode, namespaceCode, envCode, ownerAppCode, callerAppCode),
                            closureDigest(), publication, declared.toolId(), declared.name());
                    if (selected != null && !selected.equals(material)) throw invalid();
                    selected = material;
                }
                return Objects.requireNonNull(selected);
            } catch (IllegalArgumentException | NullPointerException malformed) {
                throw unavailable();
            }
        }

        private List<SiResourceVO> directDependencies(SiResourceVO agent, DdcSiKindEnum kind) {
            var targets = new HashSet<String>();
            for (var edge : manifest.edges()) {
                if (agent.identity().versionId().equals(edge.fromVersionId())) targets.add(edge.toVersionId());
            }
            return resources.stream().filter(candidate -> kind == candidate.identity().kind()
                    && targets.contains(candidate.identity().versionId())).toList();
        }

        public Map<String, Object> toWire() {
            var wire = new LinkedHashMap<String, Object>();
            wire.put("identityTenantId", Long.toString(identityTenantId));
            wire.put("bizCode", bizCode);
            wire.put("namespaceCode", namespaceCode);
            wire.put("envCode", envCode);
            wire.put("ownerAppCode", ownerAppCode);
            wire.put("callerAppCode", callerAppCode);
            wire.put("manifest", JSON.convertValue(DdcConfigCanonicalJson.parse(manifest.canonicalBytes()), OBJECT_WIRE));
            wire.put("resources", resources.stream().map(SiResourceVO::toWire).toList());
            return java.util.Collections.unmodifiableMap(wire);
        }

        public static FrozenSiBundleVO fromExactWire(Map<String, Object> wire) {
            if (wire == null || !BUNDLE_FIELDS.equals(wire.keySet()) || !(wire.get("resources") instanceof List<?> rows)
                    || rows.size() > MAX_RESOURCES) {
                throw invalid();
            }
            var manifest = JSON.convertValue(wire.get("manifest"), DdcSiDependencyManifestVO.class);
            String identityTenant = required(wire, "identityTenantId");
            if (!identityTenant.matches("[1-9][0-9]{0,18}")) throw invalid();
            return new FrozenSiBundleVO(Long.parseLong(identityTenant), required(wire, "bizCode"), required(wire, "namespaceCode"),
                    required(wire, "envCode"), optionalOwner(wire), required(wire, "callerAppCode"), manifest,
                    rows.stream().map(TianjiSiExecutionBundleService::readResource).toList());
        }

        @Override public String toString() { return "FrozenSiBundleVO[redacted]"; }
    }

    private static boolean applicable(List<TemplateDependencyRefVO> references, TemplateDependencyRefVO expected) {
        boolean present = false;
        for (var reference : references) {
            if (reference.key().equals(expected.key()) && reference.version() == expected.version()) {
                if (!reference.equals(expected)) throw invalid();
                present = true;
            }
        }
        return present;
    }

    private record DeclaredToolVO(String toolId, String name) { }

    private static DeclaredToolVO declaredTool(SiResourceVO publication, String key) {
        JsonNode selected = null;
        var tools = publication.body().path("tools");
        if (!tools.isArray()) throw invalid();
        for (var tool : tools) {
            if (key.equals(tool.path("toolId").textValue()) || key.equals(tool.path("name").textValue())) {
                if (selected != null) throw invalid();
                selected = tool;
            }
        }
        if (selected == null || !selected.path("enabled").isBoolean() || !selected.path("enabled").booleanValue()) throw invalid();
        String name = selected.path("name").textValue();
        String id = selected.has("toolId") ? selected.path("toolId").textValue() : name;
        coordinate(id);
        coordinate(name);
        return new DeclaredToolVO(id, name);
    }

    public record SiResourceVO(VersionIdentityVO identity, String key, @Nullable String ownerAppCode, String specJson) {
        public SiResourceVO {
            Objects.requireNonNull(identity, "SI resource identity");
            coordinate(key);
            owner(ownerAppCode);
            if (specJson == null || specJson.length() > MAX_RESOURCE_BYTES
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(specJson)) throw invalid();
            byte[] body = specJson.getBytes(StandardCharsets.UTF_8);
            if (body.length > MAX_RESOURCE_BYTES || !bodyDigest(body).equals(identity.digestSha256())
                    || !DdcConfigCanonicalJson.parse(body).isObject()) throw invalid();
        }

        private JsonNode body() { return DdcConfigCanonicalJson.parse(specJson.getBytes(StandardCharsets.UTF_8)); }

        private boolean matches(TemplateDependencyRefVO ref) { return key.equals(ref.key()) && versionMatches(ref); }

        private boolean versionMatches(TemplateDependencyRefVO ref) {
            return identity.versionNo().equals(Long.toString(ref.version())) && identity.digestSha256().equals(ref.contentSha256());
        }

        private boolean declaresTool(String name) {
            var tools = body().path("tools");
            if (!tools.isArray()) return false;
            int matches = 0;
            for (var tool : tools) {
                if (tool.path("enabled").isBoolean() && tool.path("enabled").booleanValue()
                        && (name.equals(tool.path("toolId").textValue()) || name.equals(tool.path("name").textValue()))) {
                    matches++;
                }
            }
            return matches == 1;
        }

        private Map<String, Object> toWire() {
            var wire = new LinkedHashMap<String, Object>();
            wire.put("identity", JSON.convertValue(identity, OBJECT_WIRE));
            wire.put("key", key);
            wire.put("ownerAppCode", ownerAppCode);
            wire.put("specJson", specJson);
            return java.util.Collections.unmodifiableMap(wire);
        }

        @Override public String toString() { return "SiResourceVO[redacted]"; }
    }

    private static SiResourceVO readResource(Object value) {
        if (!(value instanceof Map<?, ?> row) || !RESOURCE_FIELDS.equals(row.keySet())) throw invalid();
        return new SiResourceVO(JSON.convertValue(row.get("identity"), VersionIdentityVO.class),
                required(row, "key"), optionalOwner(row), required(row, "specJson"));
    }

    private static SiResourceVO one(List<SiResourceVO> resources) {
        if (resources.size() != 1) throw invalid();
        return resources.getFirst();
    }

    private static String required(Map<?, ?> wire, String key) {
        if (wire.get(key) instanceof String value && !value.isBlank()) return value;
        throw invalid();
    }

    private static String optionalOwner(Map<?, ?> wire) {
        return wire.get("ownerAppCode") == null ? null : required(wire, "ownerAppCode");
    }

    private static void owner(@Nullable String value) {
        if (value != null) coordinate(value);
    }

    private static void coordinate(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}")) throw invalid();
    }

    private static String bodyDigest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("SI_PINNED_HISTORY_UNAVAILABLE");
    }
}
