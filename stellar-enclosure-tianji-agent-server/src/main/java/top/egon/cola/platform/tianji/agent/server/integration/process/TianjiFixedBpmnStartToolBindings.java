package top.egon.cola.platform.tianji.agent.server.integration.process;

import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessReleaseQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fixed host routing metadata only; matching a mapping is not current requester or executor authority. */
public final class TianjiFixedBpmnStartToolBindings {
    private final Map<List<Object>, ResolvedBindingVO> routes;
    private final List<BindingVO> bindings;

    public TianjiFixedBpmnStartToolBindings(Collection<ToolDescriptor> descriptors, Collection<BindingVO> bindings) {
        if (descriptors == null || descriptors.isEmpty() || descriptors.size() > 256 || bindings == null || bindings.isEmpty() || bindings.size() > 256) throw invalid();
        var registered = new HashMap<String, ToolDescriptor>();
        for (var descriptor : descriptors) if (descriptor == null || registered.putIfAbsent(descriptor.versionKey(), descriptor) != null) throw invalid();
        var configured = new ArrayList<>(bindings); var routes = new HashMap<List<Object>, ResolvedBindingVO>(); var definitionHashes = new HashMap<List<Object>, String>();
        for (var binding : configured) {
            if (binding == null) throw invalid();
            var descriptor = registered.get(binding.tool().key() + '@' + binding.tool().version());
            var resolved = new ResolvedBindingVO(descriptor, binding);
            var origin = List.<Object>of(binding.tenantId(), binding.ownerIssuerFingerprint(), binding.principalType(), binding.definitionKey(), binding.definitionVersion());
            var slot = new ArrayList<>(origin); slot.add(binding.tool().key()); slot.add(binding.tool().version());
            String previous = definitionHashes.putIfAbsent(origin, binding.definitionContentSha256());
            if (routes.putIfAbsent(List.copyOf(slot), resolved) != null || previous != null && !previous.equals(binding.definitionContentSha256())) throw invalid();
        }
        this.routes = Map.copyOf(routes); this.bindings = List.copyOf(configured);
    }

    public ResolvedBindingVO resolve(FixedBpmnStartToolTransport.Request request) {
        try {
            if (request == null) throw unavailable(); var run = request.run(); var actor = run.actor(); var descriptor = request.descriptor();
            if ("LEGACY".equals(actor.issuerFingerprint())) throw unavailable();
            var slot = List.<Object>of(actor.tenantId(), actor.issuerFingerprint(), actor.subjectType(), run.definitionKey(), run.definitionVersion(), descriptor.key(), descriptor.version());
            var selected = routes.get(slot);
            if (selected == null || !selected.binding().definitionContentSha256().equals(run.definitionSha256())
                    || !selected.binding().tool().equals(descriptor.reference()) || !selected.descriptor().equals(descriptor)
                    || !selected.binding().backend().bindingSha256().equals(descriptor.policy().bindingSha256())) throw unavailable();
            return selected;
        } catch (RuntimeException uncertain) { throw unavailable(); }
    }
    public List<BindingVO> bindings() { return bindings; }

    public record BackendVO(long tenantId, URI processIssuer, URI processBaseUri, URI processResourceUri,
                            String registrationId, String expectedClientId, String expectedAppId, String expectedSourceBiz,
                            String expectedSourceApp, String expectedEnvironment, String releaseVersion, String manifestSha256,
                            String processDefinitionKey, String processModelSha256, Map<String, ProcessValueTypeEnum> inputSchema, Duration timeout) {
        public BackendVO {
            try {
                if (tenantId < 1 || !text(registrationId, 128) || !text(expectedClientId, 128) || !text(expectedAppId, 128)
                        || !text(expectedSourceBiz, 128) || !text(expectedSourceApp, 128) || !text(expectedEnvironment, 128)
                        || !hash(manifestSha256) || timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0) throw invalid();
                processIssuer = secure(processIssuer, 2048); processBaseUri = secure(processBaseUri, 2048); processResourceUri = secure(processResourceUri, 512);
                releaseVersion = new GetProcessReleaseQuery(releaseVersion).releaseVersion();
                var model = new ProcessModelDTO(ProcessModelTypeEnum.BPMN, processDefinitionKey, processModelSha256, inputSchema);
                processDefinitionKey = model.definitionKey(); processModelSha256 = model.contentSha256(); inputSchema = model.inputs();
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        public String bindingSha256() {
            var schema = new HashMap<String, Object>(); inputSchema.forEach((key, value) -> schema.put(key, value.name()));
            return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_BPMN_START_BACKEND_V1", Long.toString(tenantId), processIssuer.toASCIIString(), processBaseUri.toASCIIString(),
                    processResourceUri.toASCIIString(), registrationId, expectedClientId, expectedAppId, expectedSourceBiz, expectedSourceApp, expectedEnvironment,
                    releaseVersion, manifestSha256, processDefinitionKey, processModelSha256, timeout.toString()), schema);
        }
        @Override public boolean equals(Object value) {
            if (this == value) return true; if (!(value instanceof BackendVO other)) return false;
            return tenantId == other.tenantId && processIssuer.toASCIIString().equals(other.processIssuer.toASCIIString())
                    && processBaseUri.toASCIIString().equals(other.processBaseUri.toASCIIString()) && processResourceUri.toASCIIString().equals(other.processResourceUri.toASCIIString())
                    && registrationId.equals(other.registrationId) && expectedClientId.equals(other.expectedClientId) && expectedAppId.equals(other.expectedAppId)
                    && expectedSourceBiz.equals(other.expectedSourceBiz) && expectedSourceApp.equals(other.expectedSourceApp) && expectedEnvironment.equals(other.expectedEnvironment)
                    && releaseVersion.equals(other.releaseVersion) && manifestSha256.equals(other.manifestSha256) && processDefinitionKey.equals(other.processDefinitionKey)
                    && processModelSha256.equals(other.processModelSha256) && inputSchema.equals(other.inputSchema) && timeout.equals(other.timeout);
        }
        @Override public int hashCode() {
            return Objects.hash(tenantId, processIssuer.toASCIIString(), processBaseUri.toASCIIString(), processResourceUri.toASCIIString(), registrationId, expectedClientId, expectedAppId,
                    expectedSourceBiz, expectedSourceApp, expectedEnvironment, releaseVersion, manifestSha256, processDefinitionKey, processModelSha256, inputSchema, timeout);
        }
        @Override public String toString() { return "TianjiFixedBpmnStartToolBindings.BackendVO[redacted]"; }
    }
    public record BindingVO(long tenantId, String ownerIssuerFingerprint, TrustedAgentActor.SubjectType principalType,
                            String definitionKey, long definitionVersion, String definitionContentSha256,
                            DefinitionResourceRef tool, BackendVO backend) {
        public BindingVO {
            if (tenantId < 1 || !hash(ownerIssuerFingerprint) || principalType == null || !text(definitionKey, 128) || definitionVersion < 1 || !hash(definitionContentSha256)
                    || tool == null || !key(tool.key()) || backend == null || tenantId != backend.tenantId()) throw invalid();
        }
        @Override public String toString() { return "TianjiFixedBpmnStartToolBindings.BindingVO[redacted]"; }
    }
    public record ResolvedBindingVO(ToolDescriptor descriptor, BindingVO binding) {
        public ResolvedBindingVO {
            if (descriptor == null || binding == null || !descriptor.reference().equals(binding.tool()) || (descriptor.policy().risk() != ToolPolicy.Risk.IDEMPOTENT_WRITE && descriptor.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED)
                    || !ProcessClient.START_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey()) || !binding.backend().bindingSha256().equals(descriptor.policy().bindingSha256())
                    || !descriptor.output().fields().equals(Map.of("instanceId", ToolSchema.ValueType.STRING)) || !descriptor.output().required().equals(Set.of("instanceId"))
                    || !descriptor.input().fields().keySet().equals(binding.backend().inputSchema().keySet()) || !descriptor.input().required().equals(binding.backend().inputSchema().keySet())) throw invalid();
            for (var input : binding.backend().inputSchema().entrySet()) if (!descriptor.input().fields().get(input.getKey()).name().equals(input.getValue().name())) throw invalid();
        }
        @Override public String toString() { return "TianjiFixedBpmnStartToolBindings.ResolvedBindingVO[redacted]"; }
    }

    private static URI secure(URI value, int maximum) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !value.toASCIIString().equals(value.toString()) || !value.normalize().toASCIIString().equals(value.toASCIIString()) || value.toASCIIString().length() > maximum) throw invalid();
        return value;
    }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean key(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}"); }
    private static boolean text(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TIANJI_FIXED_BPMN_START_TOOL_BINDING_INVALID"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("TIANJI_FIXED_BPMN_START_TOOL_BINDING_UNAVAILABLE"); }
}
