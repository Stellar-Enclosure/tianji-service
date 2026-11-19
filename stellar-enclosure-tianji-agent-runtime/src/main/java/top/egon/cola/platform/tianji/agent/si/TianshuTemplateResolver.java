package top.egon.cola.platform.tianji.agent.si;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiExecutionBundleVO;
import top.egon.cola.component.tianshu.model.si.DdcSiExecutionBundleVO.MaterialVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

/**
 * Resolves one immutable Agent execution graph from the authenticated fixed SI authority. The authority verifies each
 * historical child edge/source and current authorization; this consumer independently checks the returned canonical
 * topology, complete material identities, scope and body digests. Editable references and active pointers are never used.
 */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TianshuTemplateResolver {
    private static final int MAX_BODY_BYTES = DdcSiExecutionBundleVO.MAX_BODY_BYTES;
    private static final int MAX_BUNDLE_BYTES = DdcSiExecutionBundleVO.MAX_BUNDLE_BYTES;
    private static final int MAX_NODES = DdcSiExecutionBundleVO.MAX_NODES;
    private final DdcSiCatalogClient catalogClient;

    /**
     * Preserves source compatibility only. The former appCode parameter cannot prove separate owner and caller identities.
     */
    public ResolvedAgentExecutionBundle resolveBundle(long tenantId, String bizCode, String namespaceCode,
            String envCode, @Nullable String appCode, String agentKey, @Nullable Integer pinnedVersionNo) {
        throw new IllegalStateException("TIANJI_SI_EXACT_EXECUTION_REQUIRED");
    }

    /** One bounded native call outside any persistence transaction; failure never retries active metadata or references. */
    public ResolvedAgentExecutionBundle resolveBundle(@Positive long tenantId,
            @NotBlank @Size(max = 256) String bizCode, @NotBlank @Size(max = 256) String namespaceCode,
            @NotBlank @Size(max = 256) String envCode, @Nullable @Size(max = 256) String ownerAppCode,
            @NotBlank @Size(max = 256) String callerAppCode, @NotBlank @Size(max = 256) String agentKey,
            @Positive long pinnedVersionNo) {
        if (tenantId <= 0 || pinnedVersionNo <= 0) {
            throw invalidRequest();
        }
        coordinate(bizCode); coordinate(namespaceCode); coordinate(envCode); coordinate(callerAppCode); coordinate(agentKey);
        if (ownerAppCode != null) {
            coordinate(ownerAppCode);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("TIANJI_SI_RESOLUTION_TRANSACTION_FORBIDDEN");
        }
        // Authentication, exact owner selection and every node's current grant remain the native authority's concern.
        DdcSiExecutionBundleVO response = Objects.requireNonNull(catalogClient, "catalogClient")
                .resolveExecutionBundle(tenantId, bizCode, namespaceCode, envCode, ownerAppCode, callerAppCode,
                        DdcSiKindEnum.AGENT, agentKey, pinnedVersionNo)
                .orElseThrow(TianshuTemplateResolver::unavailable);
        try {
            return checked(response, tenantId, bizCode, namespaceCode, envCode, ownerAppCode, callerAppCode,
                    agentKey, pinnedVersionNo);
        } catch (IllegalArgumentException malformedProof) {
            throw unavailable();
        }
    }

    private static ResolvedAgentExecutionBundle checked(DdcSiExecutionBundleVO response, long tenantId,
            String bizCode, String namespaceCode, String envCode, @Nullable String ownerAppCode, String callerAppCode,
            String agentKey, long pinnedVersionNo) {
        if (response == null || response.root() == null || response.manifest() == null || response.nodes() == null
                || response.nodes().size() > MAX_NODES) {
            throw unavailable();
        }
        // Reconstruct the immutable proof to recheck its digest/topology even for a custom catalog port implementation.
        var supplied = response.manifest();
        var manifest = new DdcSiDependencyManifestVO(supplied.schemaVersion(), supplied.root(), supplied.source(),
                supplied.nodes(), supplied.edges(), supplied.closureDigest());
        if (manifest.nodes().size() > MAX_NODES || !Long.toString(tenantId).equals(manifest.root().tenantId())
                || !envCode.equals(manifest.root().envCode()) || manifest.root().kind() != DdcSiKindEnum.AGENT
                || !Long.toString(pinnedVersionNo).equals(manifest.root().versionNo())
                || !manifest.root().equals(response.root().identity())
                || !Objects.equals(ownerAppCode, response.root().ownerAppCode())
                || !agentKey.equals(response.root().resourceKey())) {
            throw unavailable();
        }
        Map<String, VersionIdentityVO> expected = new LinkedHashMap<>();
        expected.put(manifest.root().versionId(), manifest.root());
        manifest.nodes().forEach(identity -> expected.put(identity.versionId(), identity));
        List<MaterialVO> suppliedMaterials = new ArrayList<>(response.nodes().size() + 1);
        suppliedMaterials.add(response.root());
        suppliedMaterials.addAll(response.nodes());
        Map<String, ResolvedResourceVO> resources = new HashMap<>();
        Map<String, JsonNode> specs = new HashMap<>();
        long totalBytes = 0;
        for (MaterialVO material : suppliedMaterials) {
            if (material == null || material.identity() == null || !bizCode.equals(material.bizCode())
                    || !namespaceCode.equals(material.namespaceCode())
                    || !Long.toString(tenantId).equals(material.identity().tenantId())
                    || !envCode.equals(material.identity().envCode())
                    || !material.identity().equals(expected.get(material.identity().versionId()))) {
                throw unavailable();
            }
            coordinate(material.resourceKey());
            if (material.ownerAppCode() != null) {
                coordinate(material.ownerAppCode());
            }
            String body = material.specJson();
            if (body == null || body.length() > MAX_BODY_BYTES) {
                throw unavailable();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            totalBytes += bytes.length;
            if (bytes.length > MAX_BODY_BYTES || totalBytes > MAX_BUNDLE_BYTES
                    || !sha256(bytes).equals(material.identity().digestSha256())) {
                throw unavailable();
            }
            JsonNode spec = DdcConfigCanonicalJson.parse(bytes);
            if (!spec.isObject()) {
                throw unavailable();
            }
            var frozen = new ResolvedResourceVO(material.identity(), material.resourceKey(), material.ownerAppCode(), body);
            if (resources.putIfAbsent(material.identity().versionId(), frozen) != null) {
                throw unavailable();
            }
            specs.put(material.identity().versionId(), spec);
        }
        if (resources.size() != expected.size()) {
            throw unavailable();
        }
        JsonNode agentSpec = specs.get(manifest.root().versionId());
        String modelKey = literal(agentSpec.get("modelKey"), true);
        String instruction = literal(agentSpec.get("systemInstruction"), false);
        JsonNode runtime = agentSpec.get("runtime");
        if (runtime != null && !runtime.isNull() && !runtime.isObject()) {
            throw unavailable();
        }
        int maxSteps = integer(runtime, "maxSteps", 8, 100);
        int timeoutSeconds = integer(runtime, "timeoutSeconds", 60, 3600);
        List<ResolvedPrompt> prompts = new ArrayList<>();
        List<ResolvedSkill> skills = new ArrayList<>();
        List<ResolvedMcpServer> mcpServers = new ArrayList<>();
        List<ResolvedResourceVO> ordered = new ArrayList<>(expected.size());
        ordered.add(resources.get(manifest.root().versionId()));
        for (VersionIdentityVO identity : manifest.nodes()) {
            ResolvedResourceVO resource = resources.get(identity.versionId());
            ordered.add(resource);
            JsonNode spec = specs.get(identity.versionId());
            long version = Long.parseLong(identity.versionNo());
            switch (identity.kind()) {
                case PROMPT -> prompts.add(new ResolvedPrompt(resource.key(), version, identity.digestSha256(),
                        literal(spec.get("promptText"), true)));
                case SKILL -> skills.add(new ResolvedSkill(resource.key(), version, identity.digestSha256(),
                        literal(spec.get("skillMarkdown"), false)));
                case MCP -> mcpServers.add(new ResolvedMcpServer(resource.key(), version, identity.digestSha256(),
                        literal(spec.get("transport"), false), literal(spec.get("endpoint"), false),
                        literal(spec.get("command"), false), literal(spec.get("credentialRef"), false),
                        headerNames(spec.get("headers"))));
                default -> { /* Atomic nested Agent material remains in the exact graph, never silently discarded. */ }
            }
        }
        return new ResolvedAgentExecutionBundle(agentKey, pinnedVersionNo, manifest.root().digestSha256(), modelKey,
                instruction, maxSteps, timeoutSeconds, prompts, skills, mcpServers, bizCode, namespaceCode, envCode,
                ownerAppCode, callerAppCode, manifest, ordered);
    }

    private static Set<String> headerNames(@Nullable JsonNode headers) {
        if (headers == null || headers.isNull()) {
            return Set.of();
        }
        if (!headers.isObject()) {
            throw unavailable();
        }
        Set<String> names = new HashSet<>();
        headers.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    @Nullable
    private static String literal(@Nullable JsonNode value, boolean required) {
        if (value == null || value.isNull()) {
            if (required) {
                throw unavailable();
            }
            return null;
        }
        if (!value.isTextual() || required && value.textValue().isBlank()) {
            throw unavailable();
        }
        return value.textValue();
    }

    private static int integer(@Nullable JsonNode runtime, String field, int fallback, int maximum) {
        JsonNode value = runtime == null ? null : runtime.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1 || value.intValue() > maximum) {
            throw unavailable();
        }
        return value.intValue();
    }

    private static void coordinate(String value) {
        if (value == null || value.isBlank() || value.length() > 256
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw invalidRequest();
        }
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException impossible) {
            throw unavailable();
        }
    }

    private static IllegalArgumentException invalidRequest() {
        return new IllegalArgumentException("TIANJI_SI_EXECUTION_REQUEST_INVALID");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
    }

    /** Exact root plus every DAG material; source-only provenance is retained solely in the immutable manifest. */
    public record ResolvedAgentExecutionBundle(String agentKey, long versionNo, String digestSha256,
            String modelKey, @Nullable String systemInstruction, int maxSteps, int timeoutSeconds,
            List<ResolvedPrompt> prompts, List<ResolvedSkill> skills, List<ResolvedMcpServer> mcpServers,
            String bizCode, String namespaceCode, String envCode, @Nullable String ownerAppCode, String callerAppCode,
            DdcSiDependencyManifestVO dependencyManifest, List<ResolvedResourceVO> resources) {
        public ResolvedAgentExecutionBundle {
            prompts = List.copyOf(prompts); skills = List.copyOf(skills); mcpServers = List.copyOf(mcpServers);
            resources = List.copyOf(resources);
        }
        @Override public String toString() {
            return "ResolvedAgentExecutionBundle[version=" + versionNo + ", resources=" + resources.size()
                    + ", closureDigest=" + dependencyManifest.closureDigest() + "]";
        }
    }

    public record ResolvedResourceVO(VersionIdentityVO identity, String key, @Nullable String ownerAppCode, String specJson) {
        @Override public String toString() { return "ResolvedResourceVO[identity=" + identity + "]"; }
    }

    public record ResolvedPrompt(String resourceKey, long versionNo, String digestSha256, String content) {
        @Override public String toString() { return "ResolvedPrompt[version=" + versionNo + ", digest=" + digestSha256 + "]"; }
    }

    public record ResolvedSkill(String resourceKey, long versionNo, String digestSha256, @Nullable String markdown) {
        @Override public String toString() { return "ResolvedSkill[version=" + versionNo + ", digest=" + digestSha256 + "]"; }
    }

    public record ResolvedMcpServer(String resourceKey, long versionNo, String digestSha256,
            @Nullable String transport, @Nullable String endpoint, @Nullable String command, @Nullable String credentialRef,
            Set<String> headerNames) {
        public ResolvedMcpServer { headerNames = Set.copyOf(headerNames); }
        @Override public String toString() {
            return "ResolvedMcpServer[version=" + versionNo + ", digest=" + digestSha256 + "]";
        }
    }
}
