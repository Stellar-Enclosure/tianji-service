package top.egon.cola.platform.tianji.agent.si;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves immutable runtime Agent execution bundles from Tianshu SI published descriptors.
 * Enforces explicit pinnedVersionNo over app binding / active baseline and never reads DRAFT or ARCHIVED versions.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TianshuTemplateResolver {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final DdcSiCatalogClient catalogClient;

    /**
     * Resolves an immutable Agent execution bundle and its referenced Prompt, Skill, and MCP descriptors.
     */
    public ResolvedAgentExecutionBundle resolveBundle(
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode,
            @Nullable String appCode,
            String agentKey,
            @Nullable Integer pinnedVersionNo) {
        Objects.requireNonNull(catalogClient, "catalogClient");
        if (bizCode == null || bizCode.isBlank() || namespaceCode == null || namespaceCode.isBlank()
                || envCode == null || envCode.isBlank() || agentKey == null || agentKey.isBlank()) {
            throw new IllegalArgumentException("Scope and agentKey are required to resolve SI bundle");
        }

        DdcSiResourceDescriptor agent = catalogClient.resolveResource(
                        tenantId, bizCode, namespaceCode, envCode, appCode, DdcSiKindEnum.AGENT, agentKey, pinnedVersionNo)
                .filter(TianshuTemplateResolver::isExecutable)
                .orElseThrow(() -> new IllegalStateException(
                        "Tianshu SI AGENT not available or unpublished: tenant=" + tenantId
                                + " env=" + envCode + " key=" + agentKey + " version=" + pinnedVersionNo));

        Map<String, Object> spec = parseSpec(agent.getSpecJson());
        String modelKey = stringValue(spec.get("modelKey"));
        String systemInstruction = stringValue(spec.get("systemInstruction"));
        Map<String, Object> runtimeMap = mapValue(spec.get("runtime"));
        Integer maxSteps = integerValue(runtimeMap.get("maxSteps"), 8);
        Integer timeoutSeconds = integerValue(runtimeMap.get("timeoutSeconds"), 60);

        List<DdcSiReferenceDescriptor> refs = agent.getReferences() == null
                ? List.of() : agent.getReferences();

        List<ResolvedPrompt> prompts = new ArrayList<>();
        List<ResolvedSkill> skills = new ArrayList<>();
        List<ResolvedMcpServer> mcpServers = new ArrayList<>();

        for (DdcSiReferenceDescriptor ref : refs) {
            DdcSiKindEnum targetKind = toKind(ref.getTargetType());
            if (targetKind == null) {
                continue;
            }
            Optional<DdcSiResourceDescriptor> depOpt = catalogClient.resolveResource(
                    tenantId, bizCode, namespaceCode, envCode, appCode, targetKind, ref.getTargetKey(), ref.getPinnedVersionNo())
                    .filter(TianshuTemplateResolver::isExecutable);

            if (depOpt.isEmpty()) {
                if (ref.isRequired()) {
                    throw new IllegalStateException("Required SI dependency missing or unpublished: type="
                            + targetKind + " key=" + ref.getTargetKey() + " version=" + ref.getPinnedVersionNo());
                }
                log.warn("Skipping optional missing/unpublished SI dependency agent={} type={} key={} version={}",
                        agentKey, targetKind, ref.getTargetKey(), ref.getPinnedVersionNo());
                continue;
            }

            DdcSiResourceDescriptor dep = depOpt.get();
            Map<String, Object> depSpec = parseSpec(dep.getSpecJson());
            int resolvedVersion = dep.getActiveVersionNo() != null ? dep.getActiveVersionNo() : 1;
            switch (targetKind) {
                case PROMPT -> prompts.add(new ResolvedPrompt(
                        dep.getResourceKey(), resolvedVersion, dep.getDigestSha256(),
                        stringValue(depSpec.get("promptText"))));
                case SKILL -> skills.add(new ResolvedSkill(
                        dep.getResourceKey(), resolvedVersion, dep.getDigestSha256(),
                        stringValue(depSpec.get("skillMarkdown"))));
                case MCP -> mcpServers.add(new ResolvedMcpServer(
                        dep.getResourceKey(), resolvedVersion, dep.getDigestSha256(),
                        stringValue(depSpec.get("transport")),
                        stringValue(depSpec.get("endpoint")),
                        stringValue(depSpec.get("command")),
                        stringValue(depSpec.get("credentialRef")),
                        Collections.unmodifiableSet(mapValue(depSpec.get("headers")).keySet())));
                default -> { }
            }
        }

        int agentVersion = agent.getActiveVersionNo() != null ? agent.getActiveVersionNo() : 1;
        return new ResolvedAgentExecutionBundle(
                agent.getResourceKey(),
                agentVersion,
                agent.getDigestSha256(),
                modelKey,
                systemInstruction,
                maxSteps,
                timeoutSeconds,
                List.copyOf(prompts),
                List.copyOf(skills),
                List.copyOf(mcpServers));
    }

    private static boolean isExecutable(DdcSiResourceDescriptor descriptor) {
        return descriptor != null
                && descriptor.getStatus() != null
                && descriptor.getStatus() != DdcSiLifecycleStatusEnum.DRAFT
                && descriptor.getStatus() != DdcSiLifecycleStatusEnum.ARCHIVED;
    }

    @Nullable
    private static DdcSiKindEnum toKind(@Nullable DdcSiReferenceTargetTypeEnum targetType) {
        if (targetType == null) {
            return null;
        }
        return switch (targetType) {
            case PROMPT -> DdcSiKindEnum.PROMPT;
            case SKILL -> DdcSiKindEnum.SKILL;
            case MCP -> DdcSiKindEnum.MCP;
            case AGENT -> DdcSiKindEnum.AGENT;
            case TIANJI_PROCESS -> null;
        };
    }

    private static Map<String, Object> parseSpec(@Nullable String specJson) {
        if (specJson == null || specJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = JSON.readValue(specJson, MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Malformed Tianshu SI specJson payload", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(@Nullable Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> copy = new LinkedHashMap<>();
            raw.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        return Collections.emptyMap();
    }

    @Nullable
    private static String stringValue(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integerValue(@Nullable Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    /** Immutable resolved Agent execution bundle with secret-safe string representations. */
    public record ResolvedAgentExecutionBundle(
            String agentKey,
            int versionNo,
            @Nullable String digestSha256,
            @Nullable String modelKey,
            @Nullable String systemInstruction,
            int maxSteps,
            int timeoutSeconds,
            List<ResolvedPrompt> prompts,
            List<ResolvedSkill> skills,
            List<ResolvedMcpServer> mcpServers) {

        @Override
        public String toString() {
            return "ResolvedAgentExecutionBundle[agentKey=" + agentKey
                    + ", versionNo=" + versionNo
                    + ", modelKey=" + modelKey
                    + ", maxSteps=" + maxSteps
                    + ", timeoutSeconds=" + timeoutSeconds
                    + ", prompts=" + prompts.size()
                    + ", skills=" + skills.size()
                    + ", mcpServers=" + mcpServers + "]";
        }
    }

    /** Resolved Prompt snapshot entry. */
    public record ResolvedPrompt(String resourceKey, int versionNo, @Nullable String digestSha256, @Nullable String content) { }

    /** Resolved Skill snapshot entry. */
    public record ResolvedSkill(String resourceKey, int versionNo, @Nullable String digestSha256, @Nullable String markdown) { }

    /** Resolved MCP server metadata with secret header values redacted. */
    public record ResolvedMcpServer(
            String resourceKey,
            int versionNo,
            @Nullable String digestSha256,
            @Nullable String transport,
            @Nullable String endpoint,
            @Nullable String command,
            @Nullable String credentialRef,
            Set<String> headerNames) {

        @Override
        public String toString() {
            return "ResolvedMcpServer[resourceKey=" + resourceKey
                    + ", versionNo=" + versionNo
                    + ", transport=" + transport
                    + ", endpoint=" + endpoint
                    + ", credentialRef=" + credentialRef
                    + ", headerNames=" + headerNames + "]";
        }
    }
}
